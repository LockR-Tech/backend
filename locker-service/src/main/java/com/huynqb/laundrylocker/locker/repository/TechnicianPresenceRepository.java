package com.huynqb.laundrylocker.locker.repository;

import com.huynqb.laundrylocker.locker.model.TechnicianPresence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TechnicianPresenceRepository extends JpaRepository<TechnicianPresence, Long> {

    Optional<TechnicianPresence> findByUserId(Long userId);

    List<TechnicianPresence> findByOnDutyTrue();

    List<TechnicianPresence> findByOnDutyTrueAndTechnicianType(String technicianType);

    List<TechnicianPresence> findAllByOrderByLastLocatedAtDesc();
}

