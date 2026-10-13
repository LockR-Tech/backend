package com.huynqb.laundrylocker.order.dto;

import java.time.LocalDateTime;

/// Một mốc đã xảy ra của đơn (tạo đơn, thanh toán, hàng vào ô, ủy quyền, hoàn tất…), dựng từ các
/// cột thời gian của đơn và lịch sử trạng thái — xem `OrderMilestones`. Danh sách trả theo thứ tự
/// cũ → mới.
///
/// @param code  mã mốc ổn định cho client (CREATED, PAID, DROP_OPENED, PARCEL_DROPPED, STORED,
///              DELEGATED, BLOCKED, FAILED, EXPIRED, PARCEL_RETURNED, CANCELED, COMPLETED)
/// @param label nhãn tiếng Việt để hiển thị thẳng
public record OrderMilestone(String code, String label, LocalDateTime occurredAt) {
}
