package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.OrderRating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRatingRepository extends JpaRepository<OrderRating, Long> {

    Optional<OrderRating> findByOrderId(Long orderId);

    List<OrderRating> findByUserIdOrderByCreatedAtDesc(Long userId);
}
