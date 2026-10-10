package com.huynqb.laundrylocker.store.dto;

import java.time.LocalDateTime;

public record StoreResponse(
        Long id,
        String name,
        String contactPhone,
        String address,
        Double latitude,
        Double longitude,
        String image,
        String description,
        Boolean active,
        Double distanceKm,
        String status,
        /// Cùng giá trị với `image` — admin web đọc tên này.
        String imageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
