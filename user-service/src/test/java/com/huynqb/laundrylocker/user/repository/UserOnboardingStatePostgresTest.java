package com.huynqb.laundrylocker.user.repository;

import com.huynqb.laundrylocker.user.model.UserOnboardingState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Bảng `user_onboarding_state` (Flyway V9) chạy thật trên Postgres: JSONB đọc/ghi đúng kiểu, mặc định của cột.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class UserOnboardingStatePostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("user_db")
                    .withUsername("user_user")
                    .withPassword("user_pass");

    @Autowired private TestEntityManager em;
    @Autowired private UserOnboardingStateRepository repository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void seenGroupsRoundTripAsJsonb() {
        UserOnboardingState state = new UserOnboardingState();
        state.setUserId(42L);
        state.setAutoShow(false);
        Map<String, Integer> seen = new LinkedHashMap<>();
        seen.put("admin-orders", 3);
        seen.put("customer-home", 1);
        state.setSeenGroups(seen);
        repository.saveAndFlush(state);
        em.clear();

        UserOnboardingState loaded = repository.findById(42L).orElseThrow();
        assertFalse(loaded.isAutoShow());
        assertEquals(Map.of("admin-orders", 3, "customer-home", 1), loaded.getSeenGroups());
        assertEquals(3, jdbc.queryForObject(
                "SELECT (seen_groups->>'admin-orders')::int FROM user_schema.user_onboarding_state WHERE user_id = 42",
                Integer.class));
    }

    @Test
    void columnDefaultsMatchANewUser() {
        jdbc.update("INSERT INTO user_schema.user_onboarding_state (user_id) VALUES (43)");
        UserOnboardingState loaded = repository.findById(43L).orElseThrow();
        assertEquals(true, loaded.isAutoShow());
        assertEquals(Map.of(), loaded.getSeenGroups());
    }
}
