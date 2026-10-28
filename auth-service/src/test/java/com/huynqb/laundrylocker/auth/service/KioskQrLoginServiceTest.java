package com.huynqb.laundrylocker.auth.service;

import com.huynqb.laundrylocker.auth.dto.AuthResponse;
import com.huynqb.laundrylocker.auth.dto.KioskQrDtos.ConfirmResult;
import com.huynqb.laundrylocker.auth.dto.KioskQrDtos.SessionCreated;
import com.huynqb.laundrylocker.auth.dto.KioskQrDtos.SessionStatus;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Đăng nhập kiosk bằng QR (CONTRACTS §1): kiosk tạo phiên, khách xác nhận trên app, kiosk nhận token đúng một lần.
@ExtendWith(MockitoExtension.class)
class KioskQrLoginServiceTest {

    private static final long LOCKER_ID = 7L;
    private static final long CUSTOMER_ID = 44L;
    private static final String KIOSK_IP = "203.0.113.10";

    @Mock AuthService authService;

    private MutableClock clock;
    private KioskQrLoginService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-11T08:00:00Z"));
        service = new KioskQrLoginService(authService, com.huynqb.laundrylocker.auth.settings.TestAuthRules.defaults());
        service.setClock(clock);
    }

    @Test
    void qrCarriesSessionAndCodeButNotPollToken() {
        SessionCreated created = service.create(LOCKER_ID, KIOSK_IP);

        assertEquals("lockr://kiosk-login?sid=" + created.sessionId() + "&code=" + created.confirmCode(),
                created.qrContent());
        assertFalse(created.qrContent().contains(created.pollToken()));
        assertTrue(created.pollToken().length() >= 43, "≥ 32 byte url-safe");
        assertEquals(6, created.confirmCode().length());
        assertEquals(clock.instant().plusSeconds(120), created.expiresAt());
        assertEquals(2000, created.pollIntervalMs());
        assertEquals(LOCKER_ID, created.lockerId());
    }

    @Test
    void lockerIdIsRequired() {
        assertEquals("KIOSK_QR_LOCKER_REQUIRED",
                assertThrows(BusinessException.class, () -> service.create(null, KIOSK_IP)).getCode());
    }

    @Test
    void wrongPollTokenLooksLikeUnknownSession() {
        SessionCreated created = service.create(LOCKER_ID, KIOSK_IP);

        BusinessException wrongToken = assertThrows(BusinessException.class,
                () -> service.poll(created.sessionId(), "not-the-token"));
        BusinessException unknown = assertThrows(BusinessException.class,
                () -> service.poll("00000000-0000-0000-0000-000000000000", created.pollToken()));
        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.poll(created.sessionId(), null));

        for (BusinessException error : new BusinessException[] {wrongToken, unknown, missing}) {
            assertEquals("KIOSK_QR_INVALID", error.getCode());
            assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        }
    }

    @Test
    void confirmedSessionDeliversTokensExactlyOnce() {
        SessionCreated created = service.create(LOCKER_ID, KIOSK_IP);
        when(authService.issueKioskLogin(CUSTOMER_ID)).thenReturn(grant());

        assertEquals("PENDING", service.poll(created.sessionId(), created.pollToken()).status());

        ConfirmResult confirmed = service.confirm(created.sessionId(), CUSTOMER_ID, created.confirmCode().toLowerCase());
        assertTrue(confirmed.confirmed());
        assertEquals(LOCKER_ID, confirmed.lockerId());

        SessionStatus first = service.poll(created.sessionId(), created.pollToken());
        assertEquals("CONFIRMED", first.status());
        assertEquals("kiosk-access", first.accessToken());
        assertEquals("kiosk-refresh", first.refreshToken());
        assertEquals("Bearer", first.tokenType());
        assertEquals(CUSTOMER_ID, first.user().id());
        assertEquals("Nguyễn An", first.user().fullName());

        SessionStatus second = service.poll(created.sessionId(), created.pollToken());
        assertEquals("CONSUMED", second.status());
        assertNull(second.accessToken());
        assertNull(second.refreshToken());
        verify(authService, times(1)).issueKioskLogin(CUSTOMER_ID);
    }

    @Test
    void confirmNeedsSignedInCustomer() {
        SessionCreated created = service.create(LOCKER_ID, KIOSK_IP);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.confirm(created.sessionId(), null, created.confirmCode()));

        assertEquals("AUTH_REQUIRED", error.getCode());
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
        assertEquals("PENDING", service.poll(created.sessionId(), created.pollToken()).status());
    }

    @Test
    void lockedAccountCannotConfirm() {
        SessionCreated created = service.create(LOCKER_ID, KIOSK_IP);
        doThrow(new BusinessException("AUTH_ACCOUNT_INACTIVE", "locked", HttpStatus.FORBIDDEN))
                .when(authService).requireActiveAccount(CUSTOMER_ID);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.confirm(created.sessionId(), CUSTOMER_ID, created.confirmCode()));

        assertEquals("AUTH_ACCOUNT_INACTIVE", error.getCode());
        assertEquals("PENDING", service.poll(created.sessionId(), created.pollToken()).status());
        verify(authService, never()).issueKioskLogin(any());
    }

    @Test
    void expiredSessionCannotBeConfirmed() {
        SessionCreated created = service.create(LOCKER_ID, KIOSK_IP);
        clock.advance(Duration.ofSeconds(120));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.confirm(created.sessionId(), CUSTOMER_ID, created.confirmCode()));

        assertEquals("KIOSK_QR_EXPIRED", error.getCode());
        assertEquals(HttpStatus.GONE, error.getStatus());
        assertEquals("EXPIRED", service.poll(created.sessionId(), created.pollToken()).status());
    }

    @Test
    void confirmedButNotCollectedBeforeExpiryNeverIssuesTokens() {
        SessionCreated created = service.create(LOCKER_ID, KIOSK_IP);
        service.confirm(created.sessionId(), CUSTOMER_ID, created.confirmCode());
        clock.advance(Duration.ofSeconds(121));

        assertEquals("EXPIRED", service.poll(created.sessionId(), created.pollToken()).status());
        verify(authService, never()).issueKioskLogin(any());
    }

    @Test
    void secondConfirmIsRejected() {
        SessionCreated created = service.create(LOCKER_ID, KIOSK_IP);
        service.confirm(created.sessionId(), CUSTOMER_ID, created.confirmCode());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.confirm(created.sessionId(), 45L, created.confirmCode()));

        assertEquals("KIOSK_QR_ALREADY_USED", error.getCode());
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void fiveWrongCodesCancelTheSession() {
        SessionCreated created = service.create(LOCKER_ID, KIOSK_IP);
        String wrong = created.confirmCode().equals("AAAAAA") ? "BBBBBB" : "AAAAAA";

        for (int attempt = 1; attempt <= 5; attempt++) {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.confirm(created.sessionId(), CUSTOMER_ID, wrong));
            assertEquals("KIOSK_QR_INVALID", error.getCode());
        }

        assertEquals("CANCELLED", service.poll(created.sessionId(), created.pollToken()).status());
        assertEquals("KIOSK_QR_INVALID", assertThrows(BusinessException.class,
                () -> service.confirm(created.sessionId(), CUSTOMER_ID, created.confirmCode())).getCode());
    }

    @Test
    void accountLockedBeforeCollectionCancelsSession() {
        SessionCreated created = service.create(LOCKER_ID, KIOSK_IP);
        service.confirm(created.sessionId(), CUSTOMER_ID, created.confirmCode());
        when(authService.issueKioskLogin(CUSTOMER_ID))
                .thenThrow(new BusinessException("AUTH_ACCOUNT_INACTIVE", "locked", HttpStatus.FORBIDDEN));

        SessionStatus status = service.poll(created.sessionId(), created.pollToken());

        assertEquals("CANCELLED", status.status());
        assertNull(status.accessToken());
    }

    @Test
    void kioskCanCancelPendingSession() {
        SessionCreated created = service.create(LOCKER_ID, KIOSK_IP);

        assertEquals("CANCELLED", service.cancel(created.sessionId(), created.pollToken()).status());
        assertEquals("KIOSK_QR_INVALID", assertThrows(BusinessException.class,
                () -> service.confirm(created.sessionId(), CUSTOMER_ID, created.confirmCode())).getCode());
        assertEquals("KIOSK_QR_INVALID", assertThrows(BusinessException.class,
                () -> service.cancel(created.sessionId(), "not-the-token")).getCode());
    }

    @Test
    void sessionCreationIsLimitedPerLocker() {
        for (int i = 0; i < KioskQrLoginService.MAX_CREATES_PER_LOCKER; i++) {
            service.create(LOCKER_ID, "198.51.100." + i);
        }

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(LOCKER_ID, KIOSK_IP));
        assertEquals("KIOSK_QR_RATE_LIMITED", error.getCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatus());

        clock.advance(Duration.ofMinutes(1));
        service.create(LOCKER_ID, KIOSK_IP);
    }

    @Test
    void sessionCreationIsLimitedPerAddress() {
        for (int i = 0; i < KioskQrLoginService.MAX_CREATES_PER_IP; i++) {
            service.create(100L + i, KIOSK_IP);
        }

        assertEquals("KIOSK_QR_RATE_LIMITED",
                assertThrows(BusinessException.class, () -> service.create(999L, KIOSK_IP)).getCode());
    }

    @Test
    void cleanupDropsFinishedSessionsAfterRetention() {
        SessionCreated created = service.create(LOCKER_ID, KIOSK_IP);
        service.create(8L, KIOSK_IP);
        service.cancel(created.sessionId(), created.pollToken());

        clock.advance(Duration.ofSeconds(30));
        service.cleanup();
        assertEquals(2, service.openSessions());

        clock.advance(Duration.ofSeconds(31));
        service.cleanup();
        assertEquals(1, service.openSessions());

        clock.advance(Duration.ofSeconds(120));
        service.cleanup();
        assertEquals(0, service.openSessions());
    }

    private static AuthService.KioskLoginGrant grant() {
        AuthResponse auth = new AuthResponse(
                1L, CUSTOMER_ID, "kiosk-access", "kiosk-refresh", "Bearer",
                Instant.parse("2026-10-12T08:00:00Z"), Set.of("CUSTOMER"));
        return new AuthService.KioskLoginGrant(
                auth, new UserSummary(CUSTOMER_ID, null, "0909000044", "Nguyễn An", "ACTIVE", Set.of("CUSTOMER")));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
