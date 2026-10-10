package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.DroneIncidentEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DroneIncidentEvidenceRepository extends JpaRepository<DroneIncidentEvidence, Long> {
    List<DroneIncidentEvidence> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);
    boolean existsByPublicId(String publicId);
}

