package com.huynqb.laundrylocker.locker.repository;

import com.huynqb.laundrylocker.locker.model.DroneUnit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DroneUnitRepository extends JpaRepository<DroneUnit, Long> {

    List<DroneUnit> findAllByOrderByLockerIdAscCodeAsc();

    boolean existsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DroneUnit d where d.id = :id")
    Optional<DroneUnit> findByIdForUpdate(@Param("id") Long id);
}
