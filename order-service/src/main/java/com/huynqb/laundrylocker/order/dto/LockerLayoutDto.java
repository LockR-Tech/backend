package com.huynqb.laundrylocker.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LockerLayoutDto(
        Long lockerId,
        String code,
        String name,
        String status,
        Boolean landingPad,
        String landingPadStatus) {
}
