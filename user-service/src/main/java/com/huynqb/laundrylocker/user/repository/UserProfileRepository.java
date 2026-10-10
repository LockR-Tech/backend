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
}
