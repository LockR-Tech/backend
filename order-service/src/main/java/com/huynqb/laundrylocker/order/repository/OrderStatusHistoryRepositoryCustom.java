package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.OrderStatusHistory;

/// Thay `save` mặc định: dòng lịch sử MỚI của đơn drone được gắn toạ độ kiện tại lúc ghi
/// (`OrderHistoryLocator`). Mọi nơi ghi lịch sử đều đi qua đây nên không nơi nào bị sót.
public interface OrderStatusHistoryRepositoryCustom {

    <S extends OrderStatusHistory> S save(S history);
}
