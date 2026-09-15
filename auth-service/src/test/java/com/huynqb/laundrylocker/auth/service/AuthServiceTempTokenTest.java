package com.huynqb.laundrylocker.auth.service;

import com.huynqb.laundrylocker.auth.client.UserClient;
import com.huynqb.laundrylocker.auth.repository.AuthAccountRepository;
import com.huynqb.laundrylocker.auth.repository.RefreshTokenRepository;
import com.huynqb.laundrylocker.auth.repository.SocialIdentityRepository;
import com.huynqb.laundrylocker.auth.settings.AuthRules;
import com.huynqb.laundrylocker.auth.settings.TestAuthRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTempTokenTest {

    @Mock
    AuthAccountRepository authAccountRepository;
    @Mock
    RefreshTokenRepository refreshTokenRepository;
    @Mock
    SocialIdentityRepository socialIdentityRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    JwtService jwtService;
    @Mock
    UserClient userClient;
    @Mock
    EmailOtpService emailOtpService;

    @Test
    void newPhoneUserGetsTempTokenWithDefaultTtl() {
        when(authAccountRepository.findByPhoneNumber("0909000001")).thenReturn(Optional.empty());

        Map<String, Object> response = service(TestAuthRules.defaults()).phoneLogin(Map.of("phoneNumber", "0909000001"));

        assertEquals(true, response.get("isNewUser"));
        assertEquals(600L, response.get("expiresIn"));
    }

    @Test
    void tempTokenTtlFollowsAdminSetting() {
        when(authAccountRepository.findByPhoneNumber("0909000002")).thenReturn(Optional.empty());

        Map<String, Object> response = service(TestAuthRules.of(Map.of("app.auth.temp-token-ttl-seconds", 120)))
                .phoneLogin(Map.of("phoneNumber", "0909000002"));

        assertEquals(120L, response.get("expiresIn"));
        assertTrue(String.valueOf(response.get("tempToken")).startsWith("phone_registration_"));
    }

    private AuthService service(AuthRules rules) {
        return new AuthService(
                authAccountRepository, refreshTokenRepository, socialIdentityRepository, passwordEncoder,
                jwtService, userClient, emailOtpService, rules);
    }
}
