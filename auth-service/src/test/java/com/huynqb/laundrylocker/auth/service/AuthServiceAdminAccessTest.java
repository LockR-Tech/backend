package com.huynqb.laundrylocker.auth.service;

import com.huynqb.laundrylocker.auth.client.UserClient;
import com.huynqb.laundrylocker.auth.dto.AccountIdentifiersRequest;
import com.huynqb.laundrylocker.auth.dto.LoginRequest;
import com.huynqb.laundrylocker.auth.dto.RefreshTokenRequest;
import com.huynqb.laundrylocker.auth.model.AuthAccount;
import com.huynqb.laundrylocker.auth.model.RefreshToken;
import com.huynqb.laundrylocker.auth.repository.AuthAccountRepository;
import com.huynqb.laundrylocker.auth.repository.RefreshTokenRepository;
import com.huynqb.laundrylocker.auth.repository.SocialIdentityRepository;
import com.huynqb.laundrylocker.auth.settings.TestAuthRules;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// 2FA admin (OTP sai không làm mất phiên, giới hạn số lần thử) và khoá tài khoản từ admin.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceAdminAccessTest {

    private static final String EMAIL = "admin@lockr.test";

    @Mock AuthAccountRepository authAccountRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock SocialIdentityRepository socialIdentityRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock UserClient userClient;
    @Mock EmailOtpService emailOtpService;

    private AuthService service;
    private AuthAccount account;

    @BeforeEach
    void setUp() {
        service = new AuthService(
                authAccountRepository, refreshTokenRepository, socialIdentityRepository, passwordEncoder,
                jwtService, userClient, emailOtpService, TestAuthRules.defaults());
        account = new AuthAccount();
        account.setId(1L);
        account.setUserId(10L);
        account.setEmail(EMAIL);
        account.setPhoneNumber("0909000010");
        account.setPasswordHash("hash");
        when(authAccountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(authAccountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(authAccountRepository.findByUserId(10L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(userClient.getUser(10L)).thenReturn(ApiResponse.ok(
                new UserSummary(10L, EMAIL, "0909000010", "Admin", "ACTIVE", Set.of("ADMIN"))));
        when(jwtService.issue(eq(1L), eq(10L), any())).thenReturn(new JwtService.TokenPair(
                "access", "refresh", Instant.now().plusSeconds(900), Instant.now().plusSeconds(86400)));
    }

    @Test
    void wrongOtpKeepsTempTokenSoAdminCanRetry() {
        String tempToken = adminLogin();
        when(emailOtpService.verifyOtp(EMAIL, "ADMIN_2FA", "111111")).thenReturn(false);
        when(emailOtpService.verifyOtp(EMAIL, "ADMIN_2FA", "123456")).thenReturn(true);

        BusinessException typo = assertThrows(BusinessException.class, () -> verify2fa(tempToken, "111111"));
        Map<String, Object> result = verify2fa(tempToken, "123456");

        assertEquals("ADMIN_AUTH_OTP_INVALID", typo.getCode());
        assertTrue(typo.getMessage().contains("còn 4 lần thử"));
        assertEquals("access", result.get("accessToken"));
    }

    @Test
    void tempTokenIsConsumedOnlyAfterSuccessfulOtp() {
        String tempToken = adminLogin();
        when(emailOtpService.verifyOtp(EMAIL, "ADMIN_2FA", "123456")).thenReturn(true);

        verify2fa(tempToken, "123456");
        BusinessException replay = assertThrows(BusinessException.class, () -> verify2fa(tempToken, "123456"));

        assertEquals("AUTH_TEMP_TOKEN_INVALID", replay.getCode());
    }

    @Test
    void tooManyWrongOtpsInvalidateTempToken() {
        String tempToken = adminLogin();
        when(emailOtpService.verifyOtp(EMAIL, "ADMIN_2FA", "000000")).thenReturn(false);
        when(emailOtpService.verifyOtp(EMAIL, "ADMIN_2FA", "123456")).thenReturn(true);

        BusinessException last = null;
        for (int i = 0; i < AuthService.ADMIN_2FA_MAX_ATTEMPTS; i++) {
            last = assertThrows(BusinessException.class, () -> verify2fa(tempToken, "000000"));
            assertEquals("ADMIN_AUTH_OTP_INVALID", last.getCode());
        }
        BusinessException afterCap = assertThrows(BusinessException.class, () -> verify2fa(tempToken, "123456"));

        assertTrue(last.getMessage().contains("đăng nhập lại"));
        assertEquals("AUTH_TEMP_TOKEN_INVALID", afterCap.getCode());
    }

    @Test
    void inactiveAdminCannotStartTwoFactorLogin() {
        account.setStatus("INACTIVE");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.adminLogin(Map.of("email", EMAIL, "password", "secret")));

        assertEquals("AUTH_INVALID", ex.getCode());
        verify(emailOtpService, never()).sendOtp(anyString(), anyString());
    }

    @Test
    void suspendingAccountRevokesRefreshTokens() {
        RefreshToken first = new RefreshToken();
        RefreshToken second = new RefreshToken();
        when(refreshTokenRepository.findByAccountIdAndRevokedFalse(1L)).thenReturn(List.of(first, second));

        Map<String, Object> result = service.updateAccountStatus(10L, "inactive");

        assertEquals("INACTIVE", account.getStatus());
        assertEquals(2, result.get("revokedTokens"));
        assertTrue(first.getRevoked());
        assertTrue(second.getRevoked());
    }

    @Test
    void reactivatingAccountDoesNotTouchTokens() {
        account.setStatus("INACTIVE");

        service.updateAccountStatus(10L, "ACTIVE");

        assertEquals("ACTIVE", account.getStatus());
        verify(refreshTokenRepository, never()).findByAccountIdAndRevokedFalse(any());
    }

    @Test
    void statusUpdateForProfileWithoutLoginIsNoOp() {
        when(authAccountRepository.findByUserId(99L)).thenReturn(Optional.empty());

        Map<String, Object> result = service.updateAccountStatus(99L, "INACTIVE");

        assertEquals(false, result.get("accountExists"));
        verify(refreshTokenRepository, never()).findByAccountIdAndRevokedFalse(any());
    }

    @Test
    void accountCreatedInactiveGetsNoTokens() {
        when(authAccountRepository.save(any(AuthAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");

        var response = service.createAccount(new com.huynqb.laundrylocker.auth.dto.CreateAccountRequest(
                20L, "locked@lockr.test", null, "secret", Set.of("CUSTOMER"), "INACTIVE"));

        assertEquals(null, response.accessToken());
        verify(jwtService, never()).issue(any(), any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void unknownAccountStatusIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateAccountStatus(10L, "BANNED"));

        assertEquals("AUTH_STATUS_INVALID", ex.getCode());
        assertEquals("ACTIVE", account.getStatus());
    }

    @Test
    void suspendedAccountCannotLoginOrRefresh() {
        account.setStatus("INACTIVE");
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.get("tokenUse", String.class)).thenReturn("refresh");
        when(jwtService.parse("old-refresh")).thenReturn(claims);
        RefreshToken stored = new RefreshToken();
        stored.setAccountId(1L);
        stored.setExpiresAt(Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString())).thenReturn(Optional.of(stored));

        BusinessException login = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest(EMAIL, "secret")));
        BusinessException refresh = assertThrows(BusinessException.class,
                () -> service.refresh(new RefreshTokenRequest("old-refresh")));

        assertEquals("AUTH_INVALID", login.getCode());
        assertEquals("AUTH_ACCOUNT_INACTIVE", refresh.getCode());
        assertEquals(HttpStatus.FORBIDDEN, refresh.getStatus());
    }

    @Test
    void identifiersSyncRejectsEmailOwnedByAnotherAccount() {
        AuthAccount other = new AuthAccount();
        other.setId(2L);
        when(authAccountRepository.findByEmail("taken@lockr.test")).thenReturn(Optional.of(other));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateAccountIdentifiers(10L, new AccountIdentifiersRequest("taken@lockr.test", null)));

        assertEquals("AUTH_EMAIL_TAKEN", ex.getCode());
        assertEquals(EMAIL, account.getEmail());
    }

    @Test
    void identifiersSyncUpdatesChangedValuesAndResetsVerification() {
        account.setEmailVerified(true);
        account.setPhoneVerified(true);
        when(authAccountRepository.findByEmail("new@lockr.test")).thenReturn(Optional.empty());
        when(authAccountRepository.findByPhoneNumber("0909000099")).thenReturn(Optional.empty());

        service.updateAccountIdentifiers(10L, new AccountIdentifiersRequest(" new@lockr.test ", "0909000099"));

        assertEquals("new@lockr.test", account.getEmail());
        assertEquals("0909000099", account.getPhoneNumber());
        assertFalse(account.getEmailVerified());
        assertFalse(account.getPhoneVerified());
    }

    @Test
    void identifiersSyncKeepsValuesWhenFieldsAreBlank() {
        account.setEmailVerified(true);

        service.updateAccountIdentifiers(10L, new AccountIdentifiersRequest("", null));

        assertEquals(EMAIL, account.getEmail());
        assertEquals("0909000010", account.getPhoneNumber());
        assertTrue(account.getEmailVerified());
    }

    private String adminLogin() {
        Map<String, Object> response = service.adminLogin(Map.of("email", EMAIL, "password", "secret"));
        Object tempToken = response.get("tempToken");
        assertNotNull(tempToken);
        return tempToken.toString();
    }

    private Map<String, Object> verify2fa(String tempToken, String otp) {
        return service.verifyAdmin2fa(Map.of("tempToken", tempToken, "otpCode", otp));
    }
}
