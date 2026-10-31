package com.huynqb.laundrylocker.user.repository;

import com.huynqb.laundrylocker.user.model.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/// Xoá mềm hồ sơ chạy thật trên Postgres (Flyway V7 + @SQLRestriction): hồ sơ biến khỏi ứng dụng, vẫn
/// đọc được lý do, và email/số điện thoại của người đã xoá đăng ký lại được.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class UserSoftDeletePostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("user_db")
                    .withUsername("user_user")
                    .withPassword("user_pass");

    @Autowired private TestEntityManager em;
    @Autowired private UserProfileRepository repository;

    private UserProfile profile(String email, String phone) {
        UserProfile user = new UserProfile();
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setFirstName("Ngọc");
        user.setLastName("Trần");
        return repository.saveAndFlush(user);
    }

    @Test
    void softDeletedProfileIsHiddenButKeptWithItsReason() {
        UserProfile user = profile("xoa@lockr.vn", "0900000001");

        user.setDeletedAt(LocalDateTime.now());
        user.setDeletedBy(1L);
        user.setDeletedReason("Nghỉ việc");
        repository.saveAndFlush(user);
        em.clear();

        assertFalse(repository.findById(user.getId()).isPresent());
        assertFalse(repository.findFirstByEmailIgnoreCase("xoa@lockr.vn").isPresent());
        assertFalse(repository.findFirstByPhoneNumber("0900000001").isPresent());
        List<UserProfileRepository.DeletedProfile> deleted = repository.findDeleted();
        assertEquals(1, deleted.size());
        assertEquals(user.getId(), deleted.get(0).getId());
        assertEquals("xoa@lockr.vn", deleted.get(0).getEmail());
        assertEquals("Nghỉ việc", deleted.get(0).getDeletedReason());
    }

    @Test
    void emailAndPhoneOfDeletedUserCanRegisterAgain() {
        UserProfile old = profile("lai@lockr.vn", "0900000002");
        old.setDeletedAt(LocalDateTime.now());
        old.setDeletedReason("Khách yêu cầu");
        repository.saveAndFlush(old);
        em.clear();

        UserProfile again = profile("lai@lockr.vn", "0900000002");

        assertNotEquals(old.getId(), again.getId());
    }
}
