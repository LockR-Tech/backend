package com.huynqb.laundrylocker.store.repository;

import com.huynqb.laundrylocker.store.model.StoreLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreRepository extends JpaRepository<StoreLocation, Long> {

    List<StoreLocation> findByStatusAndActiveTrue(String status);
}
