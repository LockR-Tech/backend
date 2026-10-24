package com.huynqb.laundrylocker.iot.repository;

import com.huynqb.laundrylocker.iot.model.CabinetControlLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CabinetControlLogRepository extends JpaRepository<CabinetControlLog, Long> {

    List<CabinetControlLog> findByLockerIdOrderByOccurredAtDescIdDesc(Long lockerId, Pageable page);

    boolean existsByEventId(String eventId);
}
