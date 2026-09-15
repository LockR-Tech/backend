package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.OrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {

    List<OrderDetail> findByOrderId(Long orderId);

    List<OrderDetail> findByOrderIdIn(java.util.Collection<Long> orderIds);

    void deleteByOrderId(Long orderId);
}
