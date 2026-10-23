package com.huynqb.laundrylocker.locker.dto;

import java.time.LocalDateTime;

/// Báo order-service ô có hàng của đơn bị báo sự cố (khớp `order-service/…/dto/BoxIncidentNotice`).
///
/// @param reportedAt     lúc phiếu được tạo — phí quá giờ của đơn dừng từ mốc này
/// @param confirmed      ô đã FAULT (KTV/admin/tủ báo, KTV xác nhận) ⇒ khoá mở ô bằng mã; false: khách báo,
///                       ô chờ KTV kiểm tra ⇒ chỉ dừng phí của chủ đồ
/// @param reporterUserId người báo — chưa xác nhận mà không phải chủ đơn thì order-service bỏ qua
public record BoxIncidentNotice(LocalDateTime reportedAt, Boolean confirmed, Long reporterUserId) {
}
