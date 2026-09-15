package com.huynqb.laundrylocker.order.dto.admin;

/// Các field cần dùng của StoreResponse (store-service /internal/stores/batch).
public record StoreInfo(
        Long id,
        String name,
        String contactPhone,
        String address,
        Double latitude,
        Double longitude,
        Boolean active,
        String status) {
}