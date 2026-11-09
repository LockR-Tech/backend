package com.huynqb.laundrylocker.iot.repository;

import com.huynqb.laundrylocker.iot.model.CabinetControlLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CabinetControlLogRepository extends JpaRepository<CabinetControlLog, Long> {

    List<CabinetControlLog> findByLockerIdOrderByOccurredAtDescIdDesc(Long lockerId, Pageable page);

    boolean existsByEventId(String eventId);

    /// Lệnh thử khi nghiệm thu lắp đặt (F3-G12), cũ → mới.
    List<CabinetControlLog> findByLockerIdAndActionInAndOccurredAtGreaterThanEqualOrderByOccurredAtAscIdAsc(
            Long lockerId, java.util.Collection<String> actions, java.time.LocalDateTime since);
}
