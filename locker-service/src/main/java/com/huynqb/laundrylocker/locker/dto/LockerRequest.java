package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.NotBlank;

public record LockerRequest(
        Long storeId,
        @NotBlank String code,
        @NotBlank String name,
        String status,
        String address,
        Double latitude,
        Double longitude) {
}
