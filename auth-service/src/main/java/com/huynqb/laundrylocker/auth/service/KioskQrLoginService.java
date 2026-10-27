package com.huynqb.laundrylocker.auth.service;

import com.huynqb.laundrylocker.auth.dto.AuthResponse;
import com.huynqb.laundrylocker.auth.dto.KioskQrDtos.ConfirmResult;
import com.huynqb.laundrylocker.auth.dto.KioskQrDtos.KioskUser;
import com.huynqb.laundrylocker.auth.dto.KioskQrDtos.SessionCreated;
import com.huynqb.laundrylocker.auth.dto.KioskQrDtos.SessionStatus;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.auth.settings.AuthRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// Đăng nhập kiosk bằng QR (CONTRACTS §1). Kiosk tạo phiên và hiện QR `lockr://kiosk-login?sid=..&code=..`;
/// khách đang đăng nhập trên app quét và xác nhận; kiosk poll bằng pollToken (chỉ kiosk giữ, không nằm trên
/// QR) để nhận token đúng một lần. Phiên lưu trong bộ nhớ (một instance auth-service), hết hạn sau
/// `app.auth.kiosk-qr.session-ttl-seconds` (admin chỉnh), dọn định kỳ.
@Slf4j
@Service
@RequiredArgsConstructor
public class KioskQrLoginService {

    /// Phiên đã kết thúc (hết hạn/huỷ/đã lấy token) giữ thêm một lúc để kiosk poll thấy trạng thái cuối.
    static final Duration RETENTION_AFTER_END = Duration.ofSeconds(60);
    static final Duration RATE_WINDOW = Duration.ofMinutes(1);
    static final long POLL_INTERVAL_MS = 2000;
    static final int MAX_CREATES_PER_LOCKER = 10;
    /// Nhiều kiosk cùng địa điểm có thể ra Internet qua chung một IP nên trần theo IP rộng hơn theo tủ.
    static final int MAX_CREATES_PER_IP = 30;
    static final int MAX_OPEN_SESSIONS = 5000;
    static final int CONFIRM_CODE_LENGTH = 6;
    /// Bỏ các ký tự dễ đọc nhầm (0/O, 1/I) vì khách có thể phải gõ tay mã này.
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int POLL_TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    enum Status { PENDING, CONFIRMED, EXPIRED, CANCELLED, CONSUMED }

    private final AuthService authService;
    private final AuthRules rules;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Window> createWindows = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public SessionCreated create(Long lockerId, String clientIp) {
        if (lockerId == null || lockerId <= 0) {
            throw new BusinessException("KIOSK_QR_LOCKER_REQUIRED", "Thiếu mã tủ (lockerId) để tạo phiên đăng nhập");
        }
        Instant now = clock.instant();
        if (sessions.size() >= MAX_OPEN_SESSIONS) {
            cleanup();
        }
        if (sessions.size() >= MAX_OPEN_SESSIONS
                || !tryAcquire("locker:" + lockerId, MAX_CREATES_PER_LOCKER, now)
                || (StringUtils.hasText(clientIp) && !tryAcquire("ip:" + clientIp.trim(), MAX_CREATES_PER_IP, now))) {
            throw new BusinessException(
                    "KIOSK_QR_RATE_LIMITED", "Tạo mã đăng nhập quá nhiều lần, vui lòng thử lại sau ít phút",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
        String sessionId = UUID.randomUUID().toString();
        String pollToken = randomPollToken();
        String confirmCode = randomConfirmCode();
        Session session = new Session(sha256(pollToken), confirmCode, lockerId, now.plusSeconds(rules.kioskQrSessionTtlSeconds()));
        sessions.put(sessionId, session);
        return new SessionCreated(
                sessionId, pollToken, confirmCode,
                "lockr://kiosk-login?sid=" + sessionId + "&code=" + confirmCode,
                lockerId, session.expiresAt, POLL_INTERVAL_MS);
    }

    /// Kiosk hỏi trạng thái. Lần poll đầu tiên sau khi khách xác nhận mới cấp token (status CONFIRMED kèm
    /// token), rồi phiên chuyển CONSUMED — poll lại không bao giờ nhận token lần hai.
    public SessionStatus poll(String sessionId, String pollToken) {
        Session session = requireKioskSession(sessionId, pollToken);
        synchronized (session) {
            Instant now = clock.instant();
            expireIfDue(session, now);
            if (session.status == Status.CONFIRMED) {
                AuthService.KioskLoginGrant grant;
                try {
                    grant = authService.issueKioskLogin(session.userId);
                } catch (BusinessException ex) {
                    // Tài khoản bị khoá giữa lúc khách xác nhận và lúc kiosk lấy token ⇒ huỷ phiên.
                    log.info("Kiosk QR session for locker {} cancelled: {}", session.lockerId, ex.getCode());
                    end(session, Status.CANCELLED, now);
                    return SessionStatus.of(session.status.name(), session.expiresAt);
                }
                end(session, Status.CONSUMED, now);
                AuthResponse auth = grant.auth();
                UserSummary user = grant.user();
                return new SessionStatus(
                        Status.CONFIRMED.name(),
                        session.expiresAt,
                        new KioskUser(auth.userId(), user == null ? null : user.fullName(),
                                user == null ? null : user.phoneNumber()),
                        auth.accessToken(),
                        auth.refreshToken(),
                        auth.tokenType(),
                        auth.roles());
            }
            return SessionStatus.of(session.status.name(), session.expiresAt);
        }
    }

    /// Khách xác nhận trên app. Danh tính chỉ lấy từ `X-User-Id` do gateway gắn sau khi kiểm JWT.
    public ConfirmResult confirm(String sessionId, Long userId, String confirmCode) {
        if (userId == null) {
            throw new BusinessException(
                    "AUTH_REQUIRED", "Vui lòng đăng nhập trên app để xác nhận đăng nhập kiosk", HttpStatus.UNAUTHORIZED);
        }
        Session session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null) {
            throw invalid("Mã đăng nhập kiosk không hợp lệ hoặc đã hết hiệu lực");
        }
        synchronized (session) {
            Instant now = clock.instant();
            expireIfDue(session, now);
            switch (session.status) {
                case EXPIRED -> throw new BusinessException(
                        "KIOSK_QR_EXPIRED", "Mã đăng nhập đã hết hạn, vui lòng quét mã mới trên kiosk", HttpStatus.GONE);
                case CONFIRMED, CONSUMED -> throw new BusinessException(
                        "KIOSK_QR_ALREADY_USED", "Mã đăng nhập này đã được dùng", HttpStatus.CONFLICT);
                case CANCELLED -> throw invalid("Phiên đăng nhập kiosk đã bị huỷ, vui lòng quét mã mới");
                default -> {
                    // PENDING: kiểm mã bên dưới.
                }
            }
            if (!codeMatches(session.confirmCode, confirmCode)) {
                session.failedAttempts++;
                int maxAttempts = rules.kioskQrMaxConfirmAttempts();
                int remaining = maxAttempts - session.failedAttempts;
                if (remaining <= 0) {
                    end(session, Status.CANCELLED, now);
                    throw invalid("Nhập sai mã xác nhận quá " + maxAttempts
                            + " lần — phiên đăng nhập đã bị huỷ, vui lòng quét mã mới");
                }
                throw invalid("Mã xác nhận không đúng (còn " + remaining + " lần thử)");
            }
            authService.requireActiveAccount(userId);
            session.status = Status.CONFIRMED;
            session.userId = userId;
            return new ConfirmResult(session.lockerId, null, true);
        }
    }

    /// Kiosk huỷ phiên (khách bấm quay lại, đổi sang cách đăng nhập khác...). Phiên đã kết thúc thì giữ nguyên.
    public SessionStatus cancel(String sessionId, String pollToken) {
        Session session = requireKioskSession(sessionId, pollToken);
        synchronized (session) {
            Instant now = clock.instant();
            expireIfDue(session, now);
            if (session.status == Status.PENDING || session.status == Status.CONFIRMED) {
                end(session, Status.CANCELLED, now);
            }
            return SessionStatus.of(session.status.name(), session.expiresAt);
        }
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void cleanup() {
        Instant now = clock.instant();
        sessions.values().removeIf(session -> {
            synchronized (session) {
                expireIfDue(session, now);
                return session.endedAt != null && !now.isBefore(session.endedAt.plus(RETENTION_AFTER_END));
            }
        });
        createWindows.values().removeIf(window -> !now.isBefore(window.start().plus(RATE_WINDOW)));
    }

    int openSessions() {
        return sessions.size();
    }

    /// Sai pollToken và phiên không tồn tại trả cùng một lỗi để không dò được sessionId.
    private Session requireKioskSession(String sessionId, String pollToken) {
        Session session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null || !StringUtils.hasText(pollToken)
                || !MessageDigest.isEqual(session.pollTokenHash, sha256(pollToken.trim()))) {
            throw invalid("Phiên đăng nhập kiosk không tồn tại");
        }
        return session;
    }

    private static void expireIfDue(Session session, Instant now) {
        if ((session.status == Status.PENDING || session.status == Status.CONFIRMED)
                && !now.isBefore(session.expiresAt)) {
            session.status = Status.EXPIRED;
            session.endedAt = session.expiresAt;
        }
    }

    private static void end(Session session, Status status, Instant now) {
        session.status = status;
        session.endedAt = now;
    }

    private boolean tryAcquire(String key, int limit, Instant now) {
        Window window = createWindows.compute(key, (k, current) ->
                current == null || !now.isBefore(current.start().plus(RATE_WINDOW))
                        ? new Window(now, 1)
                        : new Window(current.start(), current.count() + 1));
        return window.count() <= limit;
    }

    private static boolean codeMatches(String expected, String provided) {
        if (!StringUtils.hasText(provided)) {
            return false;
        }
        byte[] given = provided.trim().toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("KIOSK_QR_INVALID", message, HttpStatus.NOT_FOUND);
    }

    private static String randomPollToken() {
        byte[] bytes = new byte[POLL_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String randomConfirmCode() {
        StringBuilder code = new StringBuilder(CONFIRM_CODE_LENGTH);
        for (int i = 0; i < CONFIRM_CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static final class Session {
        private final byte[] pollTokenHash;
        private final String confirmCode;
        private final Long lockerId;
        private final Instant expiresAt;
        private Status status = Status.PENDING;
        private Long userId;
        private int failedAttempts;
        private Instant endedAt;

        private Session(byte[] pollTokenHash, String confirmCode, Long lockerId, Instant expiresAt) {
            this.pollTokenHash = pollTokenHash;
            this.confirmCode = confirmCode;
            this.lockerId = lockerId;
            this.expiresAt = expiresAt;
        }
    }

    private record Window(Instant start, int count) {
    }
}
