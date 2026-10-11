package com.huynqb.laundrylocker.auth.repository;

import com.huynqb.laundrylocker.auth.client.UserClient;
import com.huynqb.laundrylocker.auth.dto.CreateAccountRequest;
import com.huynqb.laundrylocker.auth.dto.LoginRequest;
import com.huynqb.laundrylocker.auth.model.AuthAccount;
import com.huynqb.laundrylocker.auth.model.RefreshToken;
import com.huynqb.laundrylocker.auth.model.SocialIdentity;
import com.huynqb.laundrylocker.auth.service.AuthService;
import com.huynqb.laundrylocker.auth.service.EmailOtpService;
import com.huynqb.laundrylocker.auth.service.JwtService;
import com.huynqb.laundrylocker.auth.settings.TestAuthRules;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/// Xoá người dùng chạy trên H2 thật với ràng buộc UNIQUE của entity: email/số điện thoại của tài khoản đã
/// xoá dùng lại được cho tài khoản mới; tài khoản cũ vẫn còn (giữ userId cho lịch sử) nhưng không đăng nhập được.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:auth-account-deletion;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false;INIT=CREATE SCHEMA IF NOT EXISTS auth_schema",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true"
})
class AuthAccountDeletionJpaTest {

    private static final String EMAIL = "khach@lockr.test";
    private static final String PHONE = "0909000010";

    @Autowired private TestEntityManager em;
    @Autowired private AuthAccountRepository accounts;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private SocialIdentityRepository socialIdentities;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final JwtService jwtService = mock(JwtService.class);
    private AuthService service;
    private Long oldAccountId;

    @BeforeEach
    void seed() {
        service = new AuthService(
                accounts, refreshTokens, socialIdentities, passwordEncoder,
                jwtService, mock(UserClient.class), mock(EmailOtpService.class), TestAuthRules.defaults());
        when(jwtService.issue(any(), any(), any())).thenAnswer(invocation -> new JwtService.TokenPair(
                "access", "refresh-" + UUID.randomUUID(), Instant.now().plusSeconds(900), Instant.now().plusSeconds(86400)));

        AuthAccount old = account(10L, EMAIL, PHONE);
        oldAccountId = old.getId();
        SocialIdentity google = new SocialIdentity();
        google.setAccountId(oldAccountId);
        google.setProvider("GOOGLE.COM");
        google.setProviderUserId("google-uid-1");
        em.persist(google);
        RefreshToken token = new RefreshToken();
        token.setAccountId(oldAccountId);
        token.setTokenHash("token-hash-1");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        em.persist(token);
        em.flush();
        em.clear();
    }

    @Test
    void deletedUsersEmailAndPhoneCanBeReusedForNewAccount() {
        service.deleteAccount(10L);
        em.flush();
        em.clear();

        service.createAccount(new CreateAccountRequest(20L, EMAIL, PHONE, "new-secret", Set.of("CUSTOMER"), null));
        em.flush();
        em.clear();

        assertEquals(20L, accounts.findByEmail(EMAIL).orElseThrow().getUserId());
        assertEquals(20L, accounts.findByPhoneNumber(PHONE).orElseThrow().getUserId());
        AuthAccount old = accounts.findById(oldAccountId).orElseThrow();
        assertEquals(10L, old.getUserId());
        assertEquals("DELETED", old.getStatus());
        assertTrue(old.getEmail().endsWith("@deleted.invalid"), old.getEmail());
        assertNull(old.getPhoneNumber());
        assertTrue(socialIdentities.findByAccountId(oldAccountId).isEmpty());
        assertTrue(refreshTokens.findByAccountIdAndRevokedFalse(oldAccountId).isEmpty());
    }

    @Test
    void severalDeletedAccountsDoNotCollide() {
        account(11L, "khach2@lockr.test", "0909000011");

        service.deleteAccount(10L);
        service.deleteAccount(11L);
        em.flush();
        em.clear();

        AuthAccount first = accounts.findByUserId(10L).orElseThrow();
        AuthAccount second = accounts.findByUserId(11L).orElseThrow();
        assertNotEquals(first.getEmail(), second.getEmail());
        assertNull(first.getPhoneNumber());
        assertNull(second.getPhoneNumber());
    }

    @Test
    void deletedAccountCannotLogInWithOldCredentials() {
        service.deleteAccount(10L);
        em.flush();
        em.clear();

        assertEquals("AUTH_INVALID", assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest(EMAIL, "secret"))).getCode());
        assertEquals("AUTH_INVALID", assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest(PHONE, "secret"))).getCode());
    }

    private AuthAccount account(Long userId, String email, String phone) {
        AuthAccount account = new AuthAccount();
        account.setUserId(userId);
        account.setEmail(email);
        account.setPhoneNumber(phone);
        account.setPasswordHash(passwordEncoder.encode("secret"));
        return em.persistAndFlush(account);
    }
}
