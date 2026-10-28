package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.DroneFlightBlock;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public interface DroneFlightBlockRepository
        extends JpaRepository<DroneFlightBlock, Long>, JpaSpecificationExecutor<DroneFlightBlock> {

    /// Số lần chặn theo nguyên nhân từ `since`: [cause, count].
    @Query("select b.cause, count(b) from DroneFlightBlock b where b.createdAt >= :since group by b.cause")
    List<Object[]> countByCauseSince(@Param("since") LocalDateTime since);

    /// Lọc tuỳ chọn; `lockerId` khớp tủ gửi, tủ nhận hoặc trạm gây chặn.
    default Page<DroneFlightBlock> search(
            String cause, String action, Long lockerId, String orderCode,
            LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Specification<DroneFlightBlock> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (cause != null) {
                predicates.add(cb.equal(root.get("cause"), cause));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (lockerId != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("sourceLockerId"), lockerId),
                        cb.equal(root.get("destinationLockerId"), lockerId),
                        cb.equal(root.get("blockedLockerId"), lockerId)));
            }
            if (orderCode != null) {
                predicates.add(cb.like(cb.upper(root.get("orderCode")), "%" + orderCode.toUpperCase() + "%"));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return findAll(spec, pageable);
    }
}
