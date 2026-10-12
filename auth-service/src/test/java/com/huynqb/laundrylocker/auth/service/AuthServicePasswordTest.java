package com.huynqb.laundrylocker.auth.service;

import com.huynqb.laundrylocker.auth.client.UserClient;
import com.huynqb.laundrylocker.auth.dto.AuthResponse;
import com.huynqb.laundrylocker.auth.dto.CreateAccountRequest;
import com.huynqb.laundrylocker.auth.model.AuthAccount;
import com.huynqb.laundrylocker.auth.model.RefreshToken;
import com.huynqb.laundrylocker.auth.repository.AuthAccountRepository;
import com.huynqb.laundrylocker.auth.repository.RefreshTokenRepository;
import com.huynqb.laundrylocker.auth.repository.SocialIdentityRepository;
import com.huynqb.laundrylocker.auth.settings.TestAuthRules;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Đổi/đặt mật khẩu từ app và cờ "đã có mật khẩu" (`hasPassword`).
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServicePasswordTest {

    @Mock AuthAccountRepository authAccountRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock SocialIdentityRepository socialIdentityRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock UserClient userClient;
    @Mock EmailOtpService emailOtpService;

    private AuthService service;
    private AuthAccount account;
    private RefreshToken otherDevice;

    @BeforeEach
    void setUp() {
        service = spy(new AuthService(
                authAccountRepository, refreshTokenRepository, socialIdentityRepository, passwordEncoder,
                jwtService, userClient, emailOtpService, TestAuthRules.defaults()));
        // Không gọi Firebase thật: coi idToken trong test chính là số điện thoại đã xác minh.
        doAnswer(inv -> inv.getArgument(0)).when(service).verifiedFirebasePhone(anyString());
        account = new AuthAccount();
        account.setId(1L);
        account.setUserId(10L);
        account.setPasswordHash("hash:old-secret");
        when(authAccountRepository.findByUserId(10L)).thenAnswer(inv -> Optional.of(account));
        when(authAccountRepository.save(any(AuthAccount.class))).thenAnswer(inv -> {
            AuthAccount saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(2L);
            }
            return saved;
        });
        otherDevice = new RefreshToken();
        otherDevice.setAccountId(1L);
        when(refreshTokenRepository.findByAccountIdAndRevokedFalse(1L)).thenReturn(List.of(otherDevice));
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenAnswer(inv -> ("hash:" + inv.getArgument(0)).equals(inv.getArgument(1)));
        when(userClient.getUser(any())).thenAnswer(inv -> ApiResponse.ok(
                new UserSummary(inv.getArgument(0), null, "0909000010", "Khách", "ACTIVE", Set.of("CUSTOMER"))));
        when(userClient.provisionUser(any())).thenReturn(ApiResponse.ok(
                new UserSummary(11L, null, "0909000011", "Khách", "ACTIVE", Set.of("CUSTOMER"))));
        when(jwtService.issue(any(), any(), any())).thenReturn(new JwtService.TokenPair(
                "new-access", "new-refresh", Instant.now().plusSeconds(900), Instant.now().plusSeconds(86400)));
    }

    @Test
    void accountWithPasswordNeedsCurrentPassword() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changePassword(10L, Map.of("newPassword", "new-secret")));

        assertEquals("AUTH_CURRENT_PASSWORD_REQUIRED", error.getCode());
        assertEquals("hash:old-secret", account.getPasswordHash());
        assertFalse(otherDevice.getRevoked());
    }

    @Test
    void wrongCurrentPasswordKeepsOldPassword() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changePassword(10L, Map.of("currentPassword", "guess", "newPassword", "new-secret")));

        assertEquals("AUTH_INVALID_PASSWORD", error.getCode());
        assertEquals("hash:old-secret", account.getPasswordHash());
    }

    @Test
    void newPasswordIsRequired() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changePassword(10L, Map.of("currentPassword", "old-secret")));

        assertEquals("AUTH_NEW_PASSWORD_REQUIRED", error.getCode());
    }

    @Test
    void changeRevokesOtherSessionsAndReturnsFreshTokensForThisDevice() {
        AuthResponse response = service.changePassword(
                10L, Map.of("currentPassword", "old-secret", "newPassword", "new-secret"));

        assertEquals("hash:new-secret", account.getPasswordHash());
        assertTrue(otherDevice.getRevoked());
        assertEquals("new-access", response.accessToken());
        assertEquals("new-refresh", response.refreshToken());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void accountWithoutOwnPasswordSetsFirstPasswordWithoutCurrent() {
        account.setPasswordSet(false);

        AuthResponse response = service.changePassword(10L, Map.of("newPassword", "first-secret"));

        assertEquals("hash:first-secret", account.getPasswordHash());
        assertTrue(account.getPasswordSet());
        assertEquals("new-access", response.accessToken());
        // Đã có mật khẩu ⇒ lần đổi sau phải nhập mật khẩu hiện tại.
        assertEquals("AUTH_CURRENT_PASSWORD_REQUIRED", assertThrows(BusinessException.class,
                () -> service.changePassword(10L, Map.of("newPassword", "other"))).getCode());
    }

    @Test
    void accountLookupReportsHasPassword() {
        AuthAccount otpOnly = new AuthAccount();
        otpOnly.setUserId(12L);
        otpOnly.setPasswordSet(false);
        when(authAccountRepository.findByUserIdIn(List.of(10L, 12L))).thenReturn(List.of(account, otpOnly));

        List<Map<String, Object>> rows = service.accountsByUsers(List.of(10L, 12L));

        assertEquals(true, rows.get(0).get("hasPassword"));
        assertEquals(false, rows.get(1).get("hasPassword"));
    }

    @Test
    void otpRegistrationWithoutPasswordIsMarkedAsNoPassword() {
        service.completeRegistration(Map.of("tempToken", phoneTempToken("0909000011"), "firstName", "An"));

        ArgumentCaptor<AuthAccount> saved = ArgumentCaptor.forClass(AuthAccount.class);
        verify(authAccountRepository).save(saved.capture());
        assertFalse(saved.getValue().getPasswordSet());
    }

    @Test
    void registrationWithPasswordIsMarkedAsHasPassword() {
        service.completeRegistration(Map.of("tempToken", phoneTempToken("0909000011"), "password", "chosen-secret"));

        ArgumentCaptor<AuthAccount> saved = ArgumentCaptor.forClass(AuthAccount.class);
        verify(authAccountRepository).save(saved.capture());
        assertTrue(saved.getValue().getPasswordSet());
        assertEquals("hash:chosen-secret", saved.getValue().getPasswordHash());
    }

    @Test
    void adminCreatedAccountWithoutPasswordIsMarkedAsNoPassword() {
        service.createAccount(new CreateAccountRequest(13L, "moi@lockr.test", null, null, Set.of("CUSTOMER"), null));

        ArgumentCaptor<AuthAccount> saved = ArgumentCaptor.forClass(AuthAccount.class);
        verify(authAccountRepository).save(saved.capture());
        assertFalse(saved.getValue().getPasswordSet());
    }

    /// tempToken mà phone-login cấp cho số chưa có tài khoản (bước bắt buộc trước complete-registration).
    private String phoneTempToken(String phone) {
        when(authAccountRepository.findByPhoneNumber(anyString())).thenReturn(Optional.empty());
        return (String) service.phoneLogin(Map.of("idToken", phone)).get("tempToken");
    }
}
