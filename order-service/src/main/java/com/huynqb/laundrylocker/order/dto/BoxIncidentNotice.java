package com.huynqb.laundrylocker.order.dto;

import java.time.LocalDateTime;

/// locker-service báo ô có hàng của đơn bị báo sự cố.
///
/// @param reportedAt     lúc phiếu được tạo — phí quá giờ dừng từ mốc này (lần quét sau phát hiện phiếu cũ
///                       thì vẫn dời mốc về đúng lúc báo, không phải lúc quét)
/// @param confirmed      sự cố đã được xác nhận (KTV khoá ô / tủ báo hỏng phần cứng) ⇒ khoá mở ô bằng mã;
///                       false: khách báo, chờ KTV kiểm tra ⇒ chỉ dừng phí, chủ đồ vẫn mở ô được
/// @param reporterUserId người báo; chưa xác nhận mà không phải chủ đơn / người nhận ⇒ không đụng tới đơn
public record BoxIncidentNotice(LocalDateTime reportedAt, Boolean confirmed, Long reporterUserId) {
}
