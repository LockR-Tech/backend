package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtDescIdDesc(Long orderId);

    /// Cũ → mới; hai dòng cùng thời điểm theo thứ tự ghi — dựng mốc thời gian của đơn.
    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);

    /// Như trên cho cả danh sách đơn trong một truy vấn (danh sách "Đơn của tôi").
    List<OrderStatusHistory> findByOrderIdInOrderByCreatedAtAscIdAsc(Collection<Long> orderIds);
}
