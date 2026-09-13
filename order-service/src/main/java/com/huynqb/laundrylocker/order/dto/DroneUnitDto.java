package com.huynqb.laundrylocker.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DroneUnitDto(
        Long id,
        Long lockerId,
        String code,
        String status,
        Integer batteryPercent,
        Boolean active) {
}
