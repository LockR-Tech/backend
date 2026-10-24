package com.huynqb.laundrylocker.iot.dto;

/// Tủ mà tài khoản đang đăng nhập được thử mở ô qua Bluetooth. `code`/`name` null khi không tra được tủ.
public record BleTestLockerResponse(Long lockerId, String code, String name) {
}
