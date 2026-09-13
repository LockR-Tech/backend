package com.huynqb.laundrylocker.locker.repository;

import com.huynqb.laundrylocker.locker.model.LockerBox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LockerBoxRepository extends JpaRepository<LockerBox, Long> {

    List<LockerBox> findByLockerId(Long lockerId);

    List<LockerBox> findByStatusAndReservedUntilBefore(String status, LocalDateTime cutoff);

    List<LockerBox> findByLockerIdAndStatusAndActiveTrue(Long lockerId, String status);

    Optional<LockerBox> findFirstByLockerIdAndStatusAndActiveTrue(Long lockerId, String status);

    List<LockerBox> findByLockerIdOrderByRowIndexAscColIndexAsc(Long lockerId);

    Optional<LockerBox> findFirstByLockerIdAndStatusAndCellTypeAndActiveTrueOrderByBoxNumberAsc(
            Long lockerId, String status, String cellType);

    Optional<LockerBox> findFirstByLockerIdAndStatusAndCellTypeAndSizeAndActiveTrueOrderByBoxNumberAsc(
            Long lockerId, String status, String cellType, String size);

    List<LockerBox> findByStatusAndActiveTrueOrderByLockerIdAscBoxNumberAsc(String status);

    long countByLockerId(Long lockerId);

    long countByLockerIdAndStatusAndActiveTrue(Long lockerId, String status);
}
