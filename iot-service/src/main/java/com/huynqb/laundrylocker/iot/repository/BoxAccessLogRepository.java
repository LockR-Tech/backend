package com.huynqb.laundrylocker.iot.repository;

import com.huynqb.laundrylocker.iot.model.BoxAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BoxAccessLogRepository extends JpaRepository<BoxAccessLog, Long> {

    List<BoxAccessLog> findByLockerIdOrderByCreatedAtDesc(Long lockerId);

    boolean existsByOrderIdAndResultAndCreatedAtAfter(Long orderId, String result, LocalDateTime after);

    /// Sự kiện kết nối/mất kết nối gần nhất của bộ điều khiển một tủ.
    Optional<BoxAccessLog> findFirstByLockerIdAndCredentialTypeInOrderByCreatedAtDescIdDesc(
            Long lockerId, Collection<String> credentialTypes);
}
