package com.huynqb.laundrylocker.locker.repository;

import com.huynqb.laundrylocker.locker.model.LockerUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LockerUnitRepository extends JpaRepository<LockerUnit, Long> {

    List<LockerUnit> findByStoreId(Long storeId);
}
