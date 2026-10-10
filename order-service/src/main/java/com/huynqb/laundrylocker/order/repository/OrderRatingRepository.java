package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.OrderRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRatingRepository extends JpaRepository<OrderRating, Long>, JpaSpecificationExecutor<OrderRating> {

    Optional<OrderRating> findByOrderId(Long orderId);

    List<OrderRating> findByUserIdOrderByCreatedAtDesc(Long userId);

    /// [số sao, số lượt] — phân bố 1–5 sao.
    @Query("select r.rating, count(r) from OrderRating r group by r.rating")
    List<Object[]> countByRating();

    long countByResolvedFalse();

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    List<OrderRating> findByCreatedAtGreaterThanEqual(LocalDateTime since);

    /// [loại đơn, điểm trung bình, số lượt] — điểm theo dịch vụ (SEND/RENTAL/DRONE_DELIVERY…).
    @Query("select o.type, avg(r.rating), count(r) from OrderRating r, LockerOrder o "
            + "where o.id = r.orderId group by o.type")
    List<Object[]> averageByOrderType();

    /// [loại khiếu nại, số lượt] từ `since`, nhiều nhất trước.
    @Query("select c.type, count(c) from OrderComplaint c where c.createdAt >= :since "
            + "group by c.type order by count(c) desc")
    List<Object[]> complaintTypesSince(@Param("since") LocalDateTime since);
}
