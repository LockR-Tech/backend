package com.huynqb.laundrylocker.auth.service;

import com.huynqb.laundrylocker.auth.client.UserClient;
import com.huynqb.laundrylocker.auth.model.AuthAccount;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/// Đăng nhập bằng số điện thoại chỉ dựa trên số trong Firebase ID token đã xác minh.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServicePhoneLoginTest {

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
        service = spy(new AuthService(
                authAccountRepository, refreshTokenRepository, socialIdentityRepository, passwordEncoder,
                jwtService, userClient, emailOtpService, TestAuthRules.defaults()));
        account = new AuthAccount();
        account.setId(5L);
        account.setUserId(50L);
        account.setPhoneNumber("0901000005");
        account.setStatus("ACTIVE");
        when(userClient.getUser(50L)).thenReturn(ApiResponse.ok(
                new UserSummary(50L, null, "0901000005", "Khách", "ACTIVE", Set.of("CUSTOMER"))));
        when(jwtService.issue(eq(5L), eq(50L), any())).thenReturn(new JwtService.TokenPair(
                "access", "refresh", Instant.now().plusSeconds(60), Instant.now().plusSeconds(600)));
    }

    @Test
    void rawPhoneNumberWithoutTokenIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.phoneLogin(Map.of("phoneNumber", "0901000005")));

        assertEquals("AUTH_PHONE_TOKEN_REQUIRED", ex.getCode());
        verifyNoInteractions(authAccountRepository, jwtService);
    }

    @Test
    void verifiedFirebasePhoneLogsIntoAccountStoredWithLocalPrefix() {
        doReturn("+84901000005").when(service).verifiedFirebasePhone("firebase-id-token");
        when(authAccountRepository.findByPhoneNumber("+84901000005")).thenReturn(Optional.empty());
        when(authAccountRepository.findByPhoneNumber("0901000005")).thenReturn(Optional.of(account));

        Map<String, Object> result = service.phoneLogin(Map.of("idToken", "firebase-id-token"));

        assertEquals("access", result.get("accessToken"));
        verify(jwtService).issue(eq(5L), eq(50L), any());
    }

    @Test
    void phoneSentNextToTokenIsIgnored() {
        doReturn("+84909999999").when(service).verifiedFirebasePhone("firebase-id-token");
        when(authAccountRepository.findByPhoneNumber(anyString())).thenReturn(Optional.empty());

        Map<String, Object> result = service.phoneLogin(
                Map.of("idToken", "firebase-id-token", "phoneNumber", "0901000005"));

        assertEquals(true, result.get("isNewUser"));
        assertEquals("+84909999999", result.get("phoneNumber"));
        assertTrue(String.valueOf(result.get("tempToken")).length() > 10);
        verify(authAccountRepository, never()).findByPhoneNumber("0901000005");
        verify(jwtService, never()).issue(any(), any(), any());
    }

    @Test
    void completeRegistrationRequiresTempToken() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.completeRegistration(Map.of("phoneNumber", "0901000005")));

        assertEquals("AUTH_TEMP_TOKEN_REQUIRED", ex.getCode());
        verifyNoInteractions(authAccountRepository);
    }
}
