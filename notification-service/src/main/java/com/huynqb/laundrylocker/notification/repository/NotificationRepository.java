package com.huynqb.laundrylocker.notification.repository;

import com.huynqb.laundrylocker.notification.model.NotificationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationRepository extends JpaRepository<NotificationMessage, Long> {

    List<NotificationMessage> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<NotificationMessage> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndIsReadFalse(Long userId);

    void deleteByUserId(Long userId);

    @Modifying
    @Query("update NotificationMessage n set n.isRead = true, n.status = 'READ', n.readAt = CURRENT_TIMESTAMP where n.userId = :userId and n.isRead = false")
    int markAllAsRead(Long userId);
}
