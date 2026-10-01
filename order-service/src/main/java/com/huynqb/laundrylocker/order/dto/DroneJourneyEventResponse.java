package com.huynqb.laundrylocker.order.dto;

import java.time.LocalDateTime;

/** Một mốc nghiệp vụ của hành trình, trả theo thứ tự mới nhất trước. */
public record DroneJourneyEventResponse(
        Long id,
        String fromStage,
        String toStage,
        Long actorUserId,
        /// Tên người thao tác; null khi mốc do hệ thống tự ghi hoặc tra cứu lỗi.
        String actorName,
        String note,
        LocalDateTime occurredAt) {
}
