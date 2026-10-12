package com.huynqb.laundrylocker.order.dto;

/// Body tuỳ chọn của `POST /api/drone-technician/drone-orders/{id}/advance`.
///
/// @param expectedStage chặng hiện tại app đang hiển thị (LAUNCHING, DEPARTED, EN_ROUTE, APPROACHING,
///                      ARRIVED). Khác chặng thật trên server ⇒ 409 `DRONE_STAGE_CHANGED`.
public record AdvanceDroneOrderRequest(String expectedStage) {
}
