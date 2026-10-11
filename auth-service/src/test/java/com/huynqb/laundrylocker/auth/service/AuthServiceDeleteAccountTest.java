package com.huynqb.laundrylocker.auth.service;

import com.huynqb.laundrylocker.auth.client.UserClient;
import com.huynqb.laundrylocker.auth.dto.AccountIdentifiersRequest;
import com.huynqb.laundrylocker.auth.dto.AuthResponse;
import com.huynqb.laundrylocker.auth.dto.CreateAccountRequest;
import com.huynqb.laundrylocker.auth.dto.LoginRequest;
import com.huynqb.laundrylocker.auth.dto.RefreshTokenRequest;
import com.huynqb.laundrylocker.auth.model.AuthAccount;
import com.huynqb.laundrylocker.auth.model.RefreshToken;
import com.huynqb.laundrylocker.auth.model.SocialIdentity;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Admin xoá người dùng: tài khoản đăng nhập bị khoá hẳn, email/số điện thoại/liên kết MXH được nhả cho
/// tài khoản mới. Repository giả tra cứu theo giá trị hiện tại của bản ghi, như truy vấn DB thật.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceDeleteAccountTest {

    private static final String EMAIL = "khach@lockr.test";
    private static final String PHONE = "0909000010";

    @Mock AuthAccountRepository authAccountRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock SocialIdentityRepository socialIdentityRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock UserClient userClient;
    @Mock EmailOtpService emailOtpService;

    private final List<AuthAccount> accounts = new ArrayList<>();
    private AuthService service;
    private AuthAccount account;
    private RefreshToken activeToken;
    private SocialIdentity google;

    @BeforeEach
    void setUp() {
        service = new AuthService(
                authAccountRepository, refreshTokenRepository, socialIdentityRepository, passwordEncoder,
                jwtService, userClient, emailOtpService, TestAuthRules.defaults());

        account = new AuthAccount();
        account.setId(1L);
        account.setUserId(10L);
        account.setEmail(EMAIL);
        account.setPhoneNumber(PHONE);
        account.setPasswordHash("hash:secret");
        account.setEmailVerified(true);
        account.setPhoneVerified(true);
        accounts.add(account);
        when(authAccountRepository.findByEmail(anyString())).thenAnswer(inv -> find(inv.getArgument(0), AuthAccount::getEmail));
        when(authAccountRepository.findByPhoneNumber(anyString()))
                .thenAnswer(inv -> find(inv.getArgument(0), AuthAccount::getPhoneNumber));
        when(authAccountRepository.findByUserId(any())).thenAnswer(inv -> find(inv.getArgument(0), AuthAccount::getUserId));
        when(authAccountRepository.findById(any())).thenAnswer(inv -> find(inv.getArgument(0), AuthAccount::getId));
        when(authAccountRepository.save(any(AuthAccount.class))).thenAnswer(inv -> {
            AuthAccount saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(accounts.size() + 1L);
                accounts.add(saved);
            }
            return saved;
        });

        activeToken = new RefreshToken();
        activeToken.setAccountId(1L);
        activeToken.setExpiresAt(Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByAccountIdAndRevokedFalse(1L))
                .thenAnswer(inv -> activeToken.getRevoked() ? List.of() : List.of(activeToken));
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenAnswer(inv -> activeToken.getRevoked() ? Optional.empty() : Optional.of(activeToken));
        Claims refreshClaims = mock(Claims.class);
        when(refreshClaims.get("tokenUse", String.class)).thenReturn("refresh");
        when(jwtService.parse("old-refresh")).thenReturn(refreshClaims);

        google = new SocialIdentity();
        google.setAccountId(1L);
        google.setProvider("GOOGLE.COM");
        google.setProviderUserId("google-uid-1");
        when(socialIdentityRepository.findByAccountId(1L)).thenReturn(List.of(google));

        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenAnswer(inv -> ("hash:" + inv.getArgument(0)).equals(inv.getArgument(1)));
        when(userClient.getUser(any())).thenAnswer(inv -> ApiResponse.ok(
                new UserSummary(inv.getArgument(0), null, null, "Khách", "ACTIVE", Set.of("CUSTOMER"))));
        when(jwtService.issue(any(), any(), any())).thenReturn(new JwtService.TokenPair(
                "access", "refresh", Instant.now().plusSeconds(900), Instant.now().plusSeconds(86400)));
    }

    @Test
    void deletingAccountReleasesIdentifiersAndRevokesTokens() {
        Map<String, Object> result = service.deleteAccount(10L);

        assertEquals("DELETED", account.getStatus());
        assertTrue(account.getEmail().matches("deleted\\+10\\+\\d+@deleted\\.invalid"), account.getEmail());
        assertNull(account.getPhoneNumber());
        assertFalse(account.getEmailVerified());
        assertFalse(account.getPhoneVerified());
        assertNotEquals("hash:secret", account.getPasswordHash());
        assertEquals(10L, account.getUserId());
        assertTrue(activeToken.getRevoked());
        verify(socialIdentityRepository).deleteAll(List.of(google));
        assertEquals(true, result.get("accountExists"));
        assertEquals(1, result.get("revokedTokens"));
    }

    @Test
    void deletedAccountCannotSignInAgain() {
        service.deleteAccount(10L);
        String placeholder = account.getEmail();

        assertEquals("AUTH_INVALID", assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest(EMAIL, "secret"))).getCode());
        assertEquals("AUTH_INVALID", assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest(PHONE, "secret"))).getCode());
        assertEquals("AUTH_INVALID", assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest(placeholder, "secret"))).getCode());
        assertEquals("AUTH_INVALID_REFRESH", assertThrows(BusinessException.class,
                () -> service.refresh(new RefreshTokenRequest("old-refresh"))).getCode());
        verify(jwtService, never()).issue(any(), any(), any());
    }

    @Test
    void releasedEmailAndPhoneCanBeUsedForNewAccount() {
        service.deleteAccount(10L);

        AuthResponse created = service.createAccount(
                new CreateAccountRequest(20L, EMAIL, PHONE, "new-secret", Set.of("CUSTOMER"), null));
        AuthResponse login = service.login(new LoginRequest(EMAIL, "new-secret"));

        assertEquals(20L, created.userId());
        assertEquals(20L, login.userId());
        assertEquals("DELETED", account.getStatus());
    }

    @Test
    void otpAndPhoneSignInTreatReleasedIdentifiersAsNewUser() {
        service.deleteAccount(10L);
        when(emailOtpService.verifyOtp(EMAIL, "EMAIL_LOGIN", "123456")).thenReturn(true);

        Map<String, Object> byEmail = service.verifyEmailOtp(Map.of("email", EMAIL, "otp", "123456"));
        Map<String, Object> byPhone = service.phoneLogin(Map.of("phoneNumber", PHONE));

        assertEquals(true, byEmail.get("isNewUser"));
        assertEquals(true, byPhone.get("isNewUser"));
        verify(jwtService, never()).issue(any(), any(), any());
    }

    @Test
    void deletedAccountCannotBeReactivatedOrGivenIdentifiersAgain() {
        service.deleteAccount(10L);
        String placeholder = account.getEmail();

        BusinessException reactivate = assertThrows(BusinessException.class,
                () -> service.updateAccountStatus(10L, "ACTIVE"));
        BusinessException reassign = assertThrows(BusinessException.class,
                () -> service.updateAccountIdentifiers(10L, new AccountIdentifiersRequest(EMAIL, PHONE)));

        assertEquals("AUTH_ACCOUNT_ALREADY_DELETED", reactivate.getCode());
        assertEquals(HttpStatus.CONFLICT, reactivate.getStatus());
        assertEquals("AUTH_ACCOUNT_ALREADY_DELETED", reassign.getCode());
        assertEquals("DELETED", account.getStatus());
        assertEquals(placeholder, account.getEmail());
        assertNull(account.getPhoneNumber());
    }

    @Test
    void deletingAgainIsSafe() {
        service.deleteAccount(10L);

        Map<String, Object> again = service.deleteAccount(10L);

        assertEquals("DELETED", account.getStatus());
        assertTrue(account.getEmail().endsWith("@deleted.invalid"));
        assertNull(account.getPhoneNumber());
        assertEquals(0, again.get("revokedTokens"));
    }

    @Test
    void deletingUserWithoutLoginAccountIsNoOp() {
        Map<String, Object> result = service.deleteAccount(99L);

        assertEquals(false, result.get("accountExists"));
        assertEquals(0, result.get("revokedTokens"));
        verify(socialIdentityRepository, never()).deleteAll(any());
        assertEquals(EMAIL, account.getEmail());
    }

    private Optional<AuthAccount> find(Object value, Function<AuthAccount, Object> field) {
        return accounts.stream().filter(candidate -> value.equals(field.apply(candidate))).findFirst();
    }
}
