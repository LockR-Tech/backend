package com.huynqb.laundrylocker.common.dto;

public record LockerBoxSummary(Long lockerId, Long boxId, String lockerCode, Integer boxNumber, String status) {
}
