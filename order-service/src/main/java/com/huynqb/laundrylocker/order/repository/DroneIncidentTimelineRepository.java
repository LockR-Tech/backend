package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.DroneIncidentTimeline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DroneIncidentTimelineRepository extends JpaRepository<DroneIncidentTimeline, Long> {
    List<DroneIncidentTimeline> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);
}

