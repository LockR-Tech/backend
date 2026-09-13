package com.huynqb.laundrylocker.locker.repository;

import com.huynqb.laundrylocker.locker.model.DroneUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DroneUnitRepository extends JpaRepository<DroneUnit, Long> {

    List<DroneUnit> findAllByOrderByLockerIdAscCodeAsc();

    boolean existsByCode(String code);
}
