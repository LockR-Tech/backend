package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.NotBlank;

public record OrderComplaintRequest(String type, @NotBlank String description) {
}
