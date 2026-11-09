package com.huynqb.laundrylocker.locker.repository;

import com.huynqb.laundrylocker.locker.model.LockerInstallationCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LockerInstallationCheckRepository extends JpaRepository<LockerInstallationCheck, Long> {

    List<LockerInstallationCheck> findByInstallationIdOrderByIdAsc(Long installationId);
}
