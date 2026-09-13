package com.huynqb.laundrylocker.loyalty.dto;

public record LoyaltyAccountResponse(Long id, Long userId, Integer points, Integer stamps, String tier) {
}
