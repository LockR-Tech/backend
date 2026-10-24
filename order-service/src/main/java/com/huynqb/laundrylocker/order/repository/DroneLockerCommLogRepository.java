package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.DroneLockerCommLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public interface DroneLockerCommLogRepository
        extends JpaRepository<DroneLockerCommLog, Long>, JpaSpecificationExecutor<DroneLockerCommLog> {

    List<DroneLockerCommLog> findBySessionIdOrderByTimestampAsc(String sessionId);

    List<DroneLockerCommLog> findByOrderIdOrderByTimestampAsc(Long orderId);

    default Page<DroneLockerCommLog> searchLogs(
            Long orderId,
            String droneId,
            Long lockerId,
            Long compartmentId,
            String eventType,
            String processingStatus,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Pageable pageable) {
        Specification<DroneLockerCommLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (orderId != null) {
                predicates.add(cb.equal(root.get("orderId"), orderId));
            }
            if (droneId != null && !droneId.isBlank()) {
                predicates.add(cb.equal(root.get("droneId"), droneId));
            }
            if (lockerId != null) {
                predicates.add(cb.equal(root.get("lockerId"), lockerId));
            }
            if (compartmentId != null) {
                predicates.add(cb.equal(root.get("compartmentId"), compartmentId));
            }
            if (eventType != null && !eventType.isBlank()) {
                predicates.add(cb.equal(root.get("eventType"), eventType));
            }
            if (processingStatus != null && !processingStatus.isBlank()) {
                predicates.add(cb.equal(root.get("processingStatus"), processingStatus));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), toDate));
            }
            if (query != null && query.getOrderList().isEmpty()) {
                query.orderBy(cb.desc(root.get("timestamp")), cb.desc(root.get("id")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return findAll(spec, pageable);
    }

    @Query("SELECT DISTINCT l.sessionId FROM DroneLockerCommLog l WHERE l.sessionId IS NOT NULL ORDER BY l.sessionId DESC")
    List<String> findRecentSessionIds(Pageable pageable);
}
