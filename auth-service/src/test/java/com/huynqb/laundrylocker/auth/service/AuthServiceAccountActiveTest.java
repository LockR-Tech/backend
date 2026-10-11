package com.huynqb.laundrylocker.auth.service;

import com.huynqb.laundrylocker.auth.client.UserClient;
import com.huynqb.laundrylocker.auth.model.AuthAccount;
import com.huynqb.laundrylocker.auth.repository.AuthAccountRepository;
import com.huynqb.laundrylocker.auth.repository.RefreshTokenRepository;
import com.huynqb.laundrylocker.auth.repository.SocialIdentityRepository;
import com.huynqb.laundrylocker.auth.settings.TestAuthRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/// Gateway dùng `isAccountActive` để chặn token của tài khoản đã bị khoá.
@ExtendWith(MockitoExtension.class)
class AuthServiceAccountActiveTest {

    @Mock AuthAccountRepository authAccountRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock SocialIdentityRepository socialIdentityRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock UserClient userClient;
    @Mock EmailOtpService emailOtpService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(
                authAccountRepository, refreshTokenRepository, socialIdentityRepository, passwordEncoder,
                jwtService, userClient, emailOtpService, TestAuthRules.defaults());
    }

    @Test
    void activeAccountIsActive() {
        when(authAccountRepository.findById(1L)).thenReturn(Optional.of(account("ACTIVE")));

        assertTrue(service.isAccountActive(1L));
    }

    @Test
    void lockedOrMissingAccountIsNotActive() {
        when(authAccountRepository.findById(1L)).thenReturn(Optional.of(account("INACTIVE")));
        when(authAccountRepository.findById(2L)).thenReturn(Optional.empty());

        assertFalse(service.isAccountActive(1L));
        assertFalse(service.isAccountActive(2L));
    }

    private static AuthAccount account(String status) {
        AuthAccount account = new AuthAccount();
        account.setId(1L);
        account.setUserId(10L);
        account.setStatus(status);
        return account;
    }
}
