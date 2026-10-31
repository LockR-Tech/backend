package com.huynqb.laundrylocker.user.repository;

import com.huynqb.laundrylocker.user.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findFirstByPhoneNumber(String phoneNumber);

    Optional<UserProfile> findFirstByEmailIgnoreCase(String email);

    /// Mốc tạo của người dùng từ `since` trở đi — biểu đồ tăng trưởng gom theo tháng ở tầng service.
    @Query("select u.createdAt from UserProfile u where u.createdAt >= :since")
    List<LocalDateTime> findCreatedAtSince(@Param("since") LocalDateTime since);

    /// Truy vấn gốc vì entity đã lọc `deleted_at IS NULL` (@SQLRestriction).
    @Query(value = """
            SELECT id AS "id", email AS "email", phone_number AS "phoneNumber",
                   first_name AS "firstName", last_name AS "lastName",
                   deleted_at AS "deletedAt", deleted_reason AS "deletedReason"
            FROM {h-schema}user_profiles
            WHERE deleted_at IS NOT NULL
            ORDER BY deleted_at DESC
            """, nativeQuery = true)
    List<DeletedProfile> findDeleted();

    interface DeletedProfile {
        Long getId();

        String getEmail();

        String getPhoneNumber();

        String getFirstName();

        String getLastName();

        LocalDateTime getDeletedAt();

        String getDeletedReason();
    }
}
