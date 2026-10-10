package com.huynqb.laundrylocker.store.dto;

import jakarta.validation.constraints.NotBlank;

/// Body tạo/sửa cửa hàng. Khi sửa, field không gửi (null) giữ nguyên giá trị hiện tại;
/// muốn xoá một field chữ (contactPhone, address, description) thì gửi chuỗi rỗng.
public record StoreRequest(
        @NotBlank String name,
        String contactPhone,
        String address,
        Double latitude,
        Double longitude,
        String image,
        String description,
        Boolean active,
        String status) {
}
