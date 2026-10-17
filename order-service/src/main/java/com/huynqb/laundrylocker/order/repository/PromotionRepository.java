package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.Promotion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    Optional<Promotion> findByCodeIgnoreCase(String code);

    List<Promotion> findByStatus(String status);

    /// Khoá hàng mã (SELECT … FOR UPDATE) khi áp vào đơn: trần tổng lượt và trần lượt mỗi khách được
    /// kiểm và cộng lượt dưới khoá, hai đơn đồng thời không cùng lọt qua lượt cuối.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Promotion p where upper(p.code) = upper(:code)")
    Optional<Promotion> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Promotion p where p.id = :id")
    Optional<Promotion> findByIdForUpdate(@Param("id") Long id);
}
