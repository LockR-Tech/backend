package com.huynqb.laundrylocker.auth.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.huynqb.laundrylocker.auth.client.UserClient;
import com.huynqb.laundrylocker.auth.dto.*;
import com.huynqb.laundrylocker.auth.model.AuthAccount;
import com.huynqb.laundrylocker.auth.model.RefreshToken;
import com.huynqb.laundrylocker.auth.model.SocialIdentity;
import com.huynqb.laundrylocker.auth.repository.AuthAccountRepository;
import com.huynqb.laundrylocker.auth.repository.RefreshTokenRepository;
import com.huynqb.laundrylocker.auth.repository.SocialIdentityRepository;
import com.huynqb.laundrylocker.auth.settings.AuthRules;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Map<String, TempToken> TEMP_TOKENS = new ConcurrentHashMap<>();
    /// Trạng thái tài khoản admin đặt được qua user-service.
    static final Set<String> ACCOUNT_STATUSES = Set.of("ACTIVE", "INACTIVE");
    /// Tài khoản của người dùng đã bị admin xoá: không cấp token, không mở lại hay đổi định danh được.
    static final String DELETED_STATUS = "DELETED";
    /// Tên miền `.invalid` (RFC 2606) không bao giờ nhận thư ⇒ email giữ chỗ không trùng email thật nào.
    private static final String DELETED_EMAIL_DOMAIN = "deleted.invalid";
    /// Số lần nhập sai OTP 2FA admin tối đa cho một token tạm; quá số này token bị huỷ, phải đăng nhập lại.

    private final AuthAccountRepository authAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SocialIdentityRepository socialIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserClient userClient;
    private final EmailOtpService emailOtpService;
    /// Thời hạn token tạm do admin cấu hình (ADR-0005).
    private final AuthRules rules;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        return provisionWithRoles(
                new RegisterRequest(
                        request.userId(),
                        request.email(),
                        request.phoneNumber(),
                        request.firstName(),
                        request.lastName(),
                        Set.of("CUSTOMER"),
                        request.password()));
    }

    @Transactional
    public AuthResponse provisionWithRoles(RegisterRequest request) {
        Long userId = request.userId();
        UserSummary user =
                userId == null
                        ? userClient
                        .provisionUser(
                                new UserProvisionRequest(
                                        request.email(),
                                        request.phoneNumber(),
                                        request.firstName(),
                                        request.lastName(),
                                        "ACTIVE",
                                        defaultRoles(request.roles())))
                        .data()
                        : userClient.getUser(userId).data();

        AuthAccount account = new AuthAccount();
        account.setUserId(user.id());
        account.setEmail(StringUtils.hasText(request.email()) ? request.email() : user.email());
        account.setPhoneNumber(
                StringUtils.hasText(request.phoneNumber()) ? request.phoneNumber() : user.phoneNumber());
        // Đăng ký OTP không nhập mật khẩu ⇒ mật khẩu ngẫu nhiên, đánh dấu chưa đặt để app cho đặt lần đầu.
        boolean passwordProvided = StringUtils.hasText(request.password());
        account.setPasswordHash(passwordEncoder.encode(passwordProvided ? request.password() : UUID.randomUUID().toString()));
        account.setPasswordSet(passwordProvided);
        return issue(authAccountRepository.save(account), user.roles());
    }

    @Transactional
    public AuthResponse createAccount(CreateAccountRequest request) {
        // Role model after the mobile split: CUSTOMER, ADMIN (web console),
        // DRONE_TECHNICIAN (drone fleet), LOCKER_TECHNICIAN (locker upkeep + IoT).
        Set<String> allowedRoles = Set.of("CUSTOMER", "ADMIN", "DRONE_TECHNICIAN", "LOCKER_TECHNICIAN");
        Set<String> roles = request.roles() == null ? Set.of("CUSTOMER") : request.roles();
        for (String role : roles) {
            if (!allowedRoles.contains(role)) {
                throw new BusinessException("AUTH_INVALID_ROLES", "Invalid role: " + role);
            }
        }

        // Kiểm tra trùng trước khi lưu. Trước đây cứ save thẳng rồi để ràng buộc
        // UNIQUE của DB nổ, nên lỗi trả về chỉ là một chuỗi constraint violation
        // — admin không biết trùng email hay trùng số điện thoại.
        if (StringUtils.hasText(request.email())
                && authAccountRepository.findByEmail(request.email()).isPresent()) {
            throw new BusinessException(
                    "AUTH_EMAIL_TAKEN",
                    "Email đã có tài khoản đăng nhập: " + request.email(),
                    org.springframework.http.HttpStatus.CONFLICT);
        }
        if (StringUtils.hasText(request.phoneNumber())
                && authAccountRepository.findByPhoneNumber(request.phoneNumber()).isPresent()) {
            throw new BusinessException(
                    "AUTH_PHONE_TAKEN",
                    "Số điện thoại đã có tài khoản đăng nhập: " + request.phoneNumber(),
                    org.springframework.http.HttpStatus.CONFLICT);
        }

        AuthAccount account = new AuthAccount();
        account.setUserId(request.userId());
        account.setEmail(request.email());
        account.setPhoneNumber(request.phoneNumber());
        String rawPassword = StringUtils.hasText(request.password()) ? request.password() : UUID.randomUUID().toString();
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        account.setPasswordSet(StringUtils.hasText(request.password()));
        String status = StringUtils.hasText(request.status()) ? request.status().trim().toUpperCase(Locale.ROOT) : "ACTIVE";
        if (!ACCOUNT_STATUSES.contains(status)) {
            throw new BusinessException("AUTH_STATUS_INVALID", "Trạng thái tài khoản không hợp lệ: " + request.status());
        }
        account.setStatus(status);
        AuthAccount saved = authAccountRepository.save(account);
        if (!"ACTIVE".equals(status)) {
            // Tài khoản tạo sẵn ở trạng thái khoá: không cấp token.
            return new AuthResponse(saved.getId(), saved.getUserId(), null, null, "Bearer", null, roles);
        }
        return issue(saved, roles);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        AuthAccount account =
                findByIdentifier(request.identifier());
        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())
                || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new BusinessException("AUTH_INVALID", "Invalid credentials");
        }
        account.setLastLoginAt(LocalDateTime.now());
        UserSummary user = userClient.getUser(account.getUserId()).data();
        return issue(account, user.roles());
    }

    @Transactional
    public AuthResponse firebaseLogin(String idToken) {
        FirebaseToken decodedToken;
        try {
            decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
        } catch (Exception e) {
            throw new BusinessException("AUTH_FIREBASE_INVALID", "Invalid Firebase ID Token: " + e.getMessage());
        }

        String uid = decodedToken.getUid();
        String email = decodedToken.getEmail();
        String phone = (String) decodedToken.getClaims().get("phone_number");
        String name = decodedToken.getName();
        String provider = "FIREBASE";
        Object signInProvider = decodedToken.getClaims().get("firebase");
        if (signInProvider instanceof Map map) {
            Object sip = map.get("sign_in_provider");
            if (sip != null) {
                provider = sip.toString().toUpperCase();
            }
        }

        Optional<SocialIdentity> existingIdentity = socialIdentityRepository.findByProviderAndProviderUserId(provider, uid);
        if (existingIdentity.isPresent()) {
            AuthAccount account = authAccountRepository.findById(existingIdentity.get().getAccountId())
                    .orElseThrow(() -> new BusinessException("AUTH_ACCOUNT_NOT_FOUND", "Account not found for social identity"));
            UserSummary user = userClient.getUser(account.getUserId()).data();
            return issue(account, user.roles());
        }

        AuthAccount account = null;
        if (StringUtils.hasText(email)) {
            account = authAccountRepository.findByEmail(email).orElse(null);
        }
        if (account == null && StringUtils.hasText(phone)) {
            account = authAccountRepository.findByPhoneNumber(phone).orElse(null);
        }

        if (account == null) {
            UserSummary user = userClient.provisionUser(new UserProvisionRequest(
                    email,
                    phone,
                    StringUtils.hasText(name) ? name : "User",
                    "",
                    "ACTIVE",
                    Set.of("CUSTOMER")
            )).data();

            account = new AuthAccount();
            account.setUserId(user.id());
            account.setEmail(email);
            account.setPhoneNumber(phone);
            account.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            account.setPasswordSet(false);
            account = authAccountRepository.save(account);
        }

        SocialIdentity socialIdentity = new SocialIdentity();
        socialIdentity.setAccountId(account.getId());
        socialIdentity.setProvider(provider);
        socialIdentity.setProviderUserId(uid);
        socialIdentityRepository.save(socialIdentity);

        UserSummary user = userClient.getUser(account.getUserId()).data();
        return issue(account, user.roles());
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        Claims claims = jwtService.parse(request.refreshToken());
        if (!"refresh".equals(claims.get("tokenUse", String.class))) {
            throw new BusinessException("AUTH_INVALID_REFRESH", "Refresh token is invalid");
        }
        RefreshToken stored =
                refreshTokenRepository
                        .findByTokenHashAndRevokedFalse(hash(request.refreshToken()))
                        .orElseThrow(() -> new BusinessException("AUTH_INVALID_REFRESH", "Refresh token is invalid"));
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            stored.setRevoked(true);
            throw new BusinessException("AUTH_REFRESH_EXPIRED", "Refresh token expired");
        }
        AuthAccount account = findAccount(stored.getAccountId());
        UserSummary user = userClient.getUser(account.getUserId()).data();
        stored.setRevoked(true);
        return issue(account, user.roles());
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenRepository.findByTokenHashAndRevokedFalse(hash(request.refreshToken())).ifPresent(token -> token.setRevoked(true));
    }

    /// Cùng điều kiện với `issue`: chỉ tài khoản ACTIVE mới được dùng token đã cấp.
    @Transactional(readOnly = true)
    public boolean isAccountActive(Long accountId) {
        return authAccountRepository.findById(accountId)
                .map(account -> "ACTIVE".equalsIgnoreCase(account.getStatus()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public AuthResponse getAccount(Long id) {
        AuthAccount account = findAccount(id);
        UserSummary user = userClient.getUser(account.getUserId()).data();
        return new AuthResponse(account.getId(), account.getUserId(), null, null, "Bearer", null, user.roles());
    }

    /**
     * Provider + verification info per userId, for admin user listing (service-to-service).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> accountsByUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return authAccountRepository.findByUserIdIn(userIds).stream()
                .map(
                        account -> {
                            Map<String, Object> info = new HashMap<>();
                            info.put("userId", account.getUserId());
                            info.put("provider", account.getAuthProvider());
                            info.put("emailVerified", account.getEmailVerified());
                            info.put("status", account.getStatus());
                            // false = chưa tự đặt mật khẩu (Google/Firebase/OTP) ⇒ app cho đặt lần đầu.
                            info.put("hasPassword", !Boolean.FALSE.equals(account.getPasswordSet()));
                            return info;
                        })
                .toList();
    }

    /// Đăng nhập bằng số điện thoại: số lấy từ Firebase ID token đã xác minh (người dùng đã nhập OTP
    /// của Firebase Phone Auth), không lấy từ số client tự gửi lên.
    @Transactional
    public Map<String, Object> phoneLogin(Map<String, Object> request) {
        String idToken = text(request, "idToken");
        if (!StringUtils.hasText(idToken)) {
            throw new BusinessException(
                    "AUTH_PHONE_TOKEN_REQUIRED", "Cần mã xác thực số điện thoại (Firebase ID token)");
        }
        String phoneNumber = verifiedFirebasePhone(idToken);
        return findAccountByPhone(phoneNumber)
                .map(
                        account -> {
                            account.setPhoneVerified(true);
                            account.setLastLoginAt(LocalDateTime.now());
                            UserSummary user = userClient.getUser(account.getUserId()).data();
                            return authMap(issue(account, user.roles()), false, phoneNumber, null);
                        })
                .orElseGet(
                        () -> {
                            String tempToken = createTempToken(phoneNumber, "PHONE_REGISTRATION", null);
                            Map<String, Object> response = new HashMap<>();
                            response.put("isNewUser", true);
                            response.put("phoneNumber", phoneNumber);
                            response.put("tempToken", tempToken);
                            response.put("expiresIn", (long) rules.tempTokenTtlSeconds());
                            return response;
                        });
    }

    /// Số điện thoại trong Firebase ID token đã xác minh chữ ký (claim `phone_number`, dạng +84…).
    String verifiedFirebasePhone(String idToken) {
        FirebaseToken decoded;
        try {
            decoded = FirebaseAuth.getInstance().verifyIdToken(idToken);
        } catch (Exception e) {
            throw new BusinessException("AUTH_FIREBASE_INVALID", "Mã xác thực số điện thoại không hợp lệ hoặc đã hết hạn");
        }
        Object phone = decoded.getClaims().get("phone_number");
        if (!(phone instanceof String value) || !StringUtils.hasText(value)) {
            throw new BusinessException("AUTH_PHONE_NOT_VERIFIED", "Mã xác thực không chứa số điện thoại đã xác minh");
        }
        return value.trim();
    }

    /// Firebase trả +84…, tài khoản có thể lưu 0… ⇒ thử cả hai dạng.
    private Optional<AuthAccount> findAccountByPhone(String phone) {
        Optional<AuthAccount> exact = authAccountRepository.findByPhoneNumber(phone);
        if (exact.isPresent()) {
            return exact;
        }
        if (phone.startsWith("+84")) {
            return authAccountRepository.findByPhoneNumber("0" + phone.substring(3));
        }
        if (phone.startsWith("0")) {
            return authAccountRepository.findByPhoneNumber("+84" + phone.substring(1));
        }
        return Optional.empty();
    }

    @Transactional
    public AuthResponse completeRegistration(Map<String, Object> request) {
        // Số điện thoại chỉ lấy từ tempToken do phoneLogin cấp sau khi xác minh Firebase ID token.
        String tempToken = text(request, "tempToken");
        if (!StringUtils.hasText(tempToken)) {
            throw new BusinessException("AUTH_TEMP_TOKEN_REQUIRED", "Phiên đăng ký đã hết hạn, vui lòng xác thực lại số điện thoại");
        }
        String phoneNumber = consumeTempToken(tempToken, "PHONE_REGISTRATION").identifier();
        return register(
                new RegisterRequest(
                        null,
                        text(request, "email"),
                        phoneNumber,
                        textOrDefault(request, "firstName", "Customer"),
                        textOrDefault(request, "lastName", ""),
                        Set.of("CUSTOMER"),
                        text(request, "password")));
    }

    @Transactional
    public AuthResponse kioskQuickRegister(Map<String, Object> request) {
        TempToken token = consumeTempToken(text(request, "tempToken"), null);
        boolean email = token.identifier().contains("@");
        return register(
                new RegisterRequest(
                        null,
                        email ? token.identifier() : null,
                        email ? null : token.identifier(),
                        textOrDefault(request, "firstName", "Khach"),
                        textOrDefault(request, "lastName", ""),
                        Set.of("CUSTOMER"),
                        text(request, "password")));
    }

    @Transactional
    public boolean sendEmailOtp(Map<String, Object> request) {
        return emailOtpService.sendOtp(text(request, "email"), "EMAIL_LOGIN");
    }

    @Transactional
    public Map<String, Object> verifyEmailOtp(Map<String, Object> request) {
        String email = normalizeEmail(text(request, "email"));
        if (!emailOtpService.verifyOtp(email, "EMAIL_LOGIN", text(request, "otp"))) {
            throw new BusinessException("AUTH_OTP_INVALID", "OTP is invalid or expired");
        }
        return authAccountRepository
                .findByEmail(email)
                .map(
                        account -> {
                            account.setEmailVerified(true);
                            account.setLastLoginAt(LocalDateTime.now());
                            UserSummary user = userClient.getUser(account.getUserId()).data();
                            return authMap(issue(account, user.roles()), false, null, null);
                        })
                .orElseGet(
                        () -> {
                            String tempToken = createTempToken(email, "EMAIL_REGISTRATION", null);
                            Map<String, Object> response = new HashMap<>();
                            response.put("isNewUser", true);
                            response.put("tempToken", tempToken);
                            response.put("expiresIn", (long) rules.tempTokenTtlSeconds());
                            return response;
                        });
    }

    @Transactional
    public AuthResponse emailCompleteRegistration(Map<String, Object> request) {
        String email = consumeTempToken(text(request, "tempToken"), "EMAIL_REGISTRATION").identifier();
        return register(
                new RegisterRequest(
                        null,
                        email,
                        text(request, "phoneNumber"),
                        textOrDefault(request, "firstName", "Customer"),
                        textOrDefault(request, "lastName", ""),
                        Set.of("CUSTOMER"),
                        text(request, "password")));
    }

    @Transactional
    public void sendPasswordResetOtp(Map<String, Object> request) {
        String email = normalizeEmail(text(request, "email"));
        authAccountRepository
                .findByEmail(email)
                .orElseThrow(() -> new BusinessException("AUTH_USER_NOT_FOUND", "User not found"));
        emailOtpService.sendOtp(email, "PASSWORD_RESET");
    }

    @Transactional
    public void resetPassword(Map<String, Object> request) {
        String email = normalizeEmail(text(request, "email"));
        String password = text(request, "newPassword");
        if (!password.equals(text(request, "confirmPassword"))) {
            throw new BusinessException("AUTH_PASSWORD_MISMATCH", "Passwords do not match");
        }
        if (!emailOtpService.verifyOtp(email, "PASSWORD_RESET", text(request, "otp"))) {
            throw new BusinessException("AUTH_OTP_INVALID", "OTP is invalid or expired");
        }
        AuthAccount account =
                authAccountRepository
                        .findByEmail(email)
                        .orElseThrow(() -> new BusinessException("AUTH_USER_NOT_FOUND", "User not found"));
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setPasswordSet(true);
        revokeRefreshTokens(account.getId());
    }

    /// Đổi mật khẩu từ app (user-service gọi, body `{currentPassword?, newPassword}`). Tài khoản đã tự đặt
    /// mật khẩu ⇒ bắt buộc đúng mật khẩu hiện tại; tài khoản chỉ có mật khẩu ngẫu nhiên (Google/OTP) ⇒ đặt
    /// lần đầu không cần. Thu hồi mọi refresh token (các máy khác phải đăng nhập lại) rồi cấp cặp token mới
    /// cho máy đang đổi để app không bị đăng xuất.
    @Transactional
    public AuthResponse changePassword(Long userId, Map<String, Object> request) {
        AuthAccount account =
                authAccountRepository
                        .findByUserId(userId)
                        .orElseThrow(() -> new BusinessException("AUTH_USER_NOT_FOUND", "User not found"));
        String newPassword = firstText(request, "newPassword", "password");
        if (!StringUtils.hasText(newPassword)) {
            throw new BusinessException("AUTH_NEW_PASSWORD_REQUIRED", "Vui lòng nhập mật khẩu mới");
        }
        if (!Boolean.FALSE.equals(account.getPasswordSet())) {
            String currentPassword = firstText(request, "currentPassword", "oldPassword");
            if (!StringUtils.hasText(currentPassword)) {
                throw new BusinessException("AUTH_CURRENT_PASSWORD_REQUIRED", "Vui lòng nhập mật khẩu hiện tại");
            }
            if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
                throw new BusinessException("AUTH_INVALID_PASSWORD", "Mật khẩu hiện tại không đúng");
            }
        }
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setPasswordSet(true);
        revokeRefreshTokens(account.getId());
        UserSummary user = userClient.getUser(account.getUserId()).data();
        return issue(account, user.roles());
    }

    /// Admin khoá/mở người dùng (user-service gọi). Khoá ⇒ thu hồi mọi refresh token để phiên đang mở
    /// không gia hạn được; access token đã cấp vẫn dùng được tới khi hết hạn (JWT không trạng thái).
    @Transactional
    public Map<String, Object> updateAccountStatus(Long userId, String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!ACCOUNT_STATUSES.contains(normalized)) {
            throw new BusinessException(
                    "AUTH_STATUS_INVALID", "Trạng thái tài khoản không hợp lệ: " + status + " (chỉ ACTIVE hoặc INACTIVE)");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("status", normalized);
        // Hồ sơ chưa có tài khoản đăng nhập thì không có gì để khoá — trả 200 thay vì 404 để
        // circuit breaker phía user-service không đếm đây là lỗi.
        Optional<AuthAccount> found = authAccountRepository.findByUserId(userId);
        result.put("accountExists", found.isPresent());
        if (found.isEmpty()) {
            result.put("revokedTokens", 0);
            return result;
        }
        AuthAccount account = found.get();
        requireNotDeleted(account);
        account.setStatus(normalized);
        result.put("revokedTokens", "INACTIVE".equals(normalized) ? revokeRefreshTokens(account.getId()) : 0);
        return result;
    }

    /// Admin đổi email/số điện thoại trên hồ sơ ⇒ định danh đăng nhập đổi theo. Field rỗng = giữ nguyên.
    /// Định danh mới chưa được xác minh nên cờ verified tương ứng về false.
    @Transactional
    public Map<String, Object> updateAccountIdentifiers(Long userId, AccountIdentifiersRequest request) {
        Optional<AuthAccount> found = authAccountRepository.findByUserId(userId);
        if (found.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            result.put("accountExists", false);
            return result;
        }
        AuthAccount account = found.get();
        requireNotDeleted(account);
        String email = request == null || !StringUtils.hasText(request.email()) ? null : request.email().trim();
        String phone = request == null || !StringUtils.hasText(request.phoneNumber()) ? null : request.phoneNumber().trim();
        if (email != null && !email.equalsIgnoreCase(account.getEmail() == null ? "" : account.getEmail())) {
            authAccountRepository.findByEmail(email)
                    .filter(other -> !other.getId().equals(account.getId()))
                    .ifPresent(other -> {
                        throw new BusinessException(
                                "AUTH_EMAIL_TAKEN", "Email đã có tài khoản đăng nhập: " + email,
                                org.springframework.http.HttpStatus.CONFLICT);
                    });
            account.setEmail(email);
            account.setEmailVerified(false);
        }
        if (phone != null && !phone.equals(account.getPhoneNumber())) {
            authAccountRepository.findByPhoneNumber(phone)
                    .filter(other -> !other.getId().equals(account.getId()))
                    .ifPresent(other -> {
                        throw new BusinessException(
                                "AUTH_PHONE_TAKEN", "Số điện thoại đã có tài khoản đăng nhập: " + phone,
                                org.springframework.http.HttpStatus.CONFLICT);
                    });
            account.setPhoneNumber(phone);
            account.setPhoneVerified(false);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("accountExists", true);
        result.put("email", account.getEmail());
        result.put("phoneNumber", account.getPhoneNumber());
        return result;
    }

    /// Admin xoá người dùng (user-service gọi trước khi xoá hồ sơ). Bản ghi được giữ (refresh token và lịch
    /// sử theo userId vẫn tham chiếu được) nhưng khoá hẳn: trạng thái DELETED, mật khẩu ngẫu nhiên, thu hồi
    /// mọi refresh token. Email đổi sang địa chỉ giữ chỗ duy nhất, bỏ số điện thoại và gỡ liên kết
    /// Google/Facebook ⇒ các định danh đó đăng ký lại được thành tài khoản mới. Gọi lại nhiều lần vẫn an toàn.
    @Transactional
    public Map<String, Object> deleteAccount(Long userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        // Như updateAccountStatus: hồ sơ chưa có tài khoản đăng nhập ⇒ 200 thay vì 404.
        Optional<AuthAccount> found = authAccountRepository.findByUserId(userId);
        result.put("accountExists", found.isPresent());
        if (found.isEmpty()) {
            result.put("revokedTokens", 0);
            return result;
        }
        AuthAccount account = found.get();
        account.setStatus(DELETED_STATUS);
        account.setEmail(deletedEmail(userId));
        account.setPhoneNumber(null);
        account.setEmailVerified(false);
        account.setPhoneVerified(false);
        account.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        account.setPasswordSet(false);
        socialIdentityRepository.deleteAll(socialIdentityRepository.findByAccountId(account.getId()));
        result.put("status", DELETED_STATUS);
        result.put("revokedTokens", revokeRefreshTokens(account.getId()));
        return result;
    }

    /// `deleted+<userId>+<epochSeconds>@deleted.invalid` — user_id là UNIQUE nên không đụng nhau, dài dưới
    /// 60 ký tự (cột email 255). Số điện thoại không cần giữ chỗ: cột cho phép NULL.
    private static String deletedEmail(Long userId) {
        return "deleted+" + userId + "+" + Instant.now().getEpochSecond() + "@" + DELETED_EMAIL_DOMAIN;
    }

    /// Mở lại hay gắn lại email/sđt cho tài khoản đã xoá sẽ cho nó đăng nhập lại và chiếm định danh của
    /// người dùng mới ⇒ từ chối.
    private static void requireNotDeleted(AuthAccount account) {
        if (DELETED_STATUS.equalsIgnoreCase(account.getStatus())) {
            throw new BusinessException(
                    "AUTH_ACCOUNT_ALREADY_DELETED", "Tài khoản đăng nhập của người dùng này đã bị xoá",
                    org.springframework.http.HttpStatus.CONFLICT);
        }
    }

    /// Đăng nhập kiosk bằng QR: khách xác nhận trên app ⇒ tài khoản đăng nhập phải còn ACTIVE.
    @Transactional(readOnly = true)
    public void requireActiveAccount(Long userId) {
        boolean active = userId != null && authAccountRepository.findByUserId(userId)
                .map(account -> "ACTIVE".equalsIgnoreCase(account.getStatus()))
                .orElse(false);
        if (!active) {
            throw accountInactive();
        }
    }

    /// Cấp token cho kiosk khi kiosk lấy kết quả phiên QR đã được khách xác nhận — cùng đường `issue`
    /// với đăng nhập OTP tại kiosk (tài khoản không ACTIVE ⇒ AUTH_ACCOUNT_INACTIVE).
    @Transactional
    public KioskLoginGrant issueKioskLogin(Long userId) {
        AuthAccount account = authAccountRepository.findByUserId(userId).orElseThrow(AuthService::accountInactive);
        UserSummary user = userClient.getUser(account.getUserId()).data();
        AuthResponse auth = issue(account, user.roles());
        account.setLastLoginAt(LocalDateTime.now());
        return new KioskLoginGrant(auth, user);
    }

    public record KioskLoginGrant(AuthResponse auth, UserSummary user) {
    }

    @Transactional
    public Map<String, Object> adminLogin(Map<String, Object> request) {
        AuthAccount account = findByIdentifier(text(request, "email"));
        // Tài khoản bị khoá báo chung "sai thông tin" như login thường, và không gửi OTP.
        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())
                || !passwordEncoder.matches(text(request, "password"), account.getPasswordHash())) {
            throw new BusinessException("AUTH_INVALID", "Invalid credentials");
        }
        UserSummary user = userClient.getUser(account.getUserId()).data();
        if (!user.roles().contains("ADMIN")) {
            throw new BusinessException("ADMIN_AUTH_NOT_ADMIN", "User is not an admin");
        }
        emailOtpService.sendOtp(account.getEmail(), "ADMIN_2FA");
        String tempToken = createTempToken(account.getEmail(), "ADMIN_2FA", account.getId());
        Map<String, Object> response = new HashMap<>();
        response.put("requiresTwoFactor", true);
        response.put("tempToken", tempToken);
        response.put("expiresIn", (long) rules.tempTokenTtlSeconds());
        response.put("maskedEmail", maskEmail(account.getEmail()));
        response.put("message", "OTP has been sent to your email");
        return response;
    }

    /// Kiểm OTP trước rồi mới tiêu token tạm: gõ sai một lần không làm mất phiên 2FA. Sai đủ
    /// `app.auth.admin-2fa-max-attempts` lần thì token tạm bị huỷ, phải đăng nhập lại từ đầu.
    @Transactional
    public Map<String, Object> verifyAdmin2fa(Map<String, Object> request) {
        String tempToken = text(request, "tempToken");
        TempToken token = peekTempToken(tempToken, "ADMIN_2FA");
        AuthAccount account = findAccount(token.accountId());
        if (!emailOtpService.verifyOtp(account.getEmail(), "ADMIN_2FA", firstText(request, "otpCode", "otp"))) {
            int remaining = recordFailedOtpAttempt(tempToken);
            throw new BusinessException(
                    "ADMIN_AUTH_OTP_INVALID",
                    remaining > 0
                            ? "Mã OTP không đúng hoặc đã hết hạn (còn " + remaining + " lần thử)"
                            : "Nhập sai OTP quá " + rules.admin2faMaxAttempts() + " lần — vui lòng đăng nhập lại");
        }
        consumeTempToken(tempToken, "ADMIN_2FA");
        UserSummary user = userClient.getUser(account.getUserId()).data();
        if (!user.roles().contains("ADMIN")) {
            throw new BusinessException("ADMIN_AUTH_NOT_ADMIN", "User is not an admin");
        }
        return authMap(issue(account, user.roles()), false, null, user.fullName());
    }

    @Transactional
    public AuthResponse adminRefresh(RefreshTokenRequest request) {
        AuthResponse response = refresh(request);
        if (!response.roles().contains("ADMIN")) {
            throw new BusinessException("ADMIN_AUTH_NOT_ADMIN", "User is not an admin");
        }
        return response;
    }

    private AuthAccount findAccount(Long id) {
        return authAccountRepository.findById(id).orElseThrow(() -> new NotFoundException("AuthAccount", id));
    }

    private AuthAccount findByIdentifier(String identifier) {
        return authAccountRepository
                .findByEmail(identifier)
                .or(() -> authAccountRepository.findByPhoneNumber(identifier))
                .orElseThrow(() -> new BusinessException("AUTH_INVALID", "Invalid credentials"));
    }

    private AuthResponse issue(AuthAccount account, Set<String> roles) {
        // Mọi đường cấp token (mật khẩu, OTP, Firebase, refresh) đều chặn tài khoản đã bị admin khoá.
        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw accountInactive();
        }
        Set<String> effectiveRoles = defaultRoles(roles);
        JwtService.TokenPair tokenPair = jwtService.issue(account.getId(), account.getUserId(), effectiveRoles);
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setAccountId(account.getId());
        refreshToken.setTokenHash(hash(tokenPair.refreshToken()));
        refreshToken.setExpiresAt(tokenPair.refreshExpiresAt());
        refreshTokenRepository.save(refreshToken);
        return new AuthResponse(
                account.getId(),
                account.getUserId(),
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                "Bearer",
                tokenPair.accessExpiresAt(),
                effectiveRoles);
    }

    private static BusinessException accountInactive() {
        return new BusinessException(
                "AUTH_ACCOUNT_INACTIVE", "Tài khoản đã bị khoá", org.springframework.http.HttpStatus.FORBIDDEN);
    }

    private Set<String> defaultRoles(Set<String> roles) {
        return roles == null || roles.isEmpty() ? Set.of("CUSTOMER") : roles;
    }

    private Map<String, Object> authMap(
            AuthResponse auth, boolean newUser, String phoneNumber, String displayName) {
        Map<String, Object> response = new HashMap<>();
        response.put("accountId", auth.accountId());
        response.put("userId", auth.userId());
        response.put("accessToken", auth.accessToken());
        response.put("refreshToken", auth.refreshToken());
        response.put("tokenType", auth.tokenType());
        response.put("expiresAt", auth.expiresAt());
        response.put("roles", auth.roles());
        response.put("isNewUser", newUser);
        if (StringUtils.hasText(phoneNumber)) {
            response.put("phoneNumber", phoneNumber);
        }
        if (StringUtils.hasText(displayName)) {
            response.put("name", displayName);
        }
        return response;
    }

    private String createTempToken(String identifier, String purpose, Long accountId) {
        String token = purpose.toLowerCase() + "_" + UUID.randomUUID();
        TEMP_TOKENS.put(token, new TempToken(identifier, purpose, accountId, Instant.now().plusSeconds(rules.tempTokenTtlSeconds()), 0));
        return token;
    }

    /// Đọc token tạm mà không tiêu nó (dùng khi còn phải kiểm OTP). Token hết hạn thì bị xoá luôn.
    private TempToken peekTempToken(String token, String expectedPurpose) {
        TempToken temp = token == null ? null : TEMP_TOKENS.get(token);
        if (temp == null || temp.expiresAt().isBefore(Instant.now())) {
            if (temp != null) {
                TEMP_TOKENS.remove(token, temp);
            }
            throw new BusinessException("AUTH_TEMP_TOKEN_INVALID", "Temporary token is invalid or expired");
        }
        if (StringUtils.hasText(expectedPurpose) && !expectedPurpose.equals(temp.purpose())) {
            throw new BusinessException("AUTH_TEMP_TOKEN_INVALID", "Temporary token purpose is invalid");
        }
        return temp;
    }

    /// Tăng đếm lần nhập sai; chạm trần thì xoá token. Trả về số lần thử còn lại (0 = token đã bị huỷ).
    private int recordFailedOtpAttempt(String token) {
        TempToken updated = TEMP_TOKENS.computeIfPresent(token, (key, temp) ->
                temp.failedAttempts() + 1 >= rules.admin2faMaxAttempts() ? null : temp.withFailedAttempt());
        return updated == null ? 0 : rules.admin2faMaxAttempts() - updated.failedAttempts();
    }

    private int revokeRefreshTokens(Long accountId) {
        List<RefreshToken> active = refreshTokenRepository.findByAccountIdAndRevokedFalse(accountId);
        active.forEach(token -> token.setRevoked(true));
        return active.size();
    }

    private TempToken consumeTempToken(String token, String expectedPurpose) {
        TempToken temp = token == null ? null : TEMP_TOKENS.remove(token);
        if (temp == null || temp.expiresAt().isBefore(Instant.now())) {
            throw new BusinessException("AUTH_TEMP_TOKEN_INVALID", "Temporary token is invalid or expired");
        }
        if (StringUtils.hasText(expectedPurpose) && !expectedPurpose.equals(temp.purpose())) {
            throw new BusinessException("AUTH_TEMP_TOKEN_INVALID", "Temporary token purpose is invalid");
        }
        return temp;
    }

    private String text(Map<String, Object> request, String key) {
        Object value = request == null ? null : request.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstText(Map<String, Object> request, String... keys) {
        for (String key : keys) {
            String value = text(request, key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String textOrDefault(Map<String, Object> request, String key, String defaultValue) {
        String value = text(request, key);
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        return (at <= 2 ? "***" : email.substring(0, 2) + "***") + email.substring(at);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash token", ex);
        }
    }

    private record TempToken(String identifier, String purpose, Long accountId, Instant expiresAt, int failedAttempts) {

        TempToken withFailedAttempt() {
            return new TempToken(identifier, purpose, accountId, expiresAt, failedAttempts + 1);
        }
    }
}
