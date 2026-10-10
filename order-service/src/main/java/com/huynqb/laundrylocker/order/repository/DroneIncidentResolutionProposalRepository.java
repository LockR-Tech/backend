package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.DroneIncidentResolutionProposal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DroneIncidentResolutionProposalRepository
        extends JpaRepository<DroneIncidentResolutionProposal, Long> {
    List<DroneIncidentResolutionProposal> findByIncidentIdOrderByProposalVersionAsc(Long incidentId);
    Optional<DroneIncidentResolutionProposal> findFirstByIncidentIdOrderByProposalVersionDesc(Long incidentId);
}
