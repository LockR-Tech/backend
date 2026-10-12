package com.huynqb.laundrylocker.locker.dto;

public record LockerLocationRequest(
        Double latitude,
        Double longitude,
        String address) {
}
