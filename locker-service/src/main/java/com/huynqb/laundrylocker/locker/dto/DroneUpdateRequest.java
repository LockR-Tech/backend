package com.huynqb.laundrylocker.locker.dto;

/// Cap nhat thong tin co ban cua drone (admin): doi tu goc va/hoac doi ma drone.
/// Cac truong null = giu nguyen gia tri cu.
public record DroneUpdateRequest(Long lockerId, String code) {
}
