package com.huynqb.laundrylocker.locker.repository;

import com.huynqb.laundrylocker.locker.model.InstallationStatus;
import com.huynqb.laundrylocker.locker.model.LockerInstallation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LockerInstallationRepository extends JpaRepository<LockerInstallation, Long> {

    boolean existsByLockerIdAndStatusIn(Long lockerId, Collection<InstallationStatus> statuses);

    Optional<LockerInstallation> findFirstByLockerIdOrderByIdDesc(Long lockerId);

    List<LockerInstallation> findByStatusInOrderByIdDesc(Collection<InstallationStatus> statuses);

    List<LockerInstallation> findAllByOrderByIdDesc();

    List<LockerInstallation> findByTechnicianIdAndStatusInOrderByScheduledAtAscIdAsc(
            Long technicianId, Collection<InstallationStatus> statuses);
}
