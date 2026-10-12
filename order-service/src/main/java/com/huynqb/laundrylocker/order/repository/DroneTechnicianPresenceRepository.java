package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.DroneTechnicianPresence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DroneTechnicianPresenceRepository extends JpaRepository<DroneTechnicianPresence, Long> {

    List<DroneTechnicianPresence> findByOnDutyTrue();

    /// Khoá dòng KTV trong lúc mời: hai đơn được thanh toán cùng lúc không mời trùng một người.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "select p from DroneTechnicianPresence p where p.userId = :userId")
    java.util.Optional<DroneTechnicianPresence> findByUserIdForUpdate(
            @org.springframework.data.repository.query.Param("userId") Long userId);
}
