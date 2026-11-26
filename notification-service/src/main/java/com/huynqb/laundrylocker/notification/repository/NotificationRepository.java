package com.huynqb.laundrylocker.notification.repository;

import com.huynqb.laundrylocker.notification.model.NotificationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository
        extends JpaRepository<NotificationMessage, Long>, JpaSpecificationExecutor<NotificationMessage> {

    List<NotificationMessage> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<NotificationMessage> findByIdAndUserId(Long id, Long userId);

    List<NotificationMessage> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndIsReadFalse(Long userId);

    void deleteByUserId(Long userId);

    @Modifying
    @Query("update NotificationMessage n set n.isRead = true, n.status = 'READ', n.readAt = CURRENT_TIMESTAMP where n.userId = :userId and n.isRead = false")
    int markAllAsRead(Long userId);

    /// Xoá mềm hàng loạt của admin (ADR-0009): giữ dòng trong DB kèm thời điểm, người xoá và lý do.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NotificationMessage n set n.deletedAt = :at, n.deletedBy = :actor, n.deletedReason = :reason "
            + "where n.id in :ids and n.deletedAt is null")
    int softDelete(@Param("ids") Collection<Long> ids, @Param("at") LocalDateTime at,
                   @Param("actor") Long actor, @Param("reason") String reason);
}
