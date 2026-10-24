package com.huynqb.laundrylocker.iot.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.iot.dto.BleOpenTokenResponse;
import com.huynqb.laundrylocker.iot.dto.BleTestGrantRequest;
import com.huynqb.laundrylocker.iot.dto.BleTestGrantResponse;
import com.huynqb.laundrylocker.iot.dto.BleTestLockerResponse;
import com.huynqb.laundrylocker.iot.service.BleTestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/// Thử mở ô qua Bluetooth (F3-G11). `/api/admin/**` chỉ ADMIN (gateway); `/api/iot/ble-test/**` mọi
/// JWT — service tự kiểm quyền admin đã cấp; `/api/iot/ble/public-key` công khai cho Pi tải khoá.
@RestController
@RequiredArgsConstructor
public class BleTestController {

    private final BleTestService bleTest;

    @GetMapping("/api/admin/iot/ble-test/lockers/{lockerId}/users")
    public ApiResponse<List<BleTestGrantResponse>> grants(@PathVariable Long lockerId) {
        return ApiResponse.ok(bleTest.grantsOf(lockerId));
    }

    @PostMapping("/api/admin/iot/ble-test/lockers/{lockerId}/users")
    public ApiResponse<BleTestGrantResponse> grant(
            @PathVariable Long lockerId,
            @Valid @RequestBody BleTestGrantRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long adminId) {
        return ApiResponse.ok("BLE_TEST_GRANTED", "Đã cho tài khoản thử mở ô qua Bluetooth",
                bleTest.grant(lockerId, request.userId(), adminId));
    }

    @DeleteMapping("/api/admin/iot/ble-test/lockers/{lockerId}/users/{userId}")
    public ApiResponse<Void> revoke(@PathVariable Long lockerId, @PathVariable Long userId) {
        bleTest.revoke(lockerId, userId);
        return ApiResponse.ok("BLE_TEST_REVOKED", "Đã thu quyền thử mở ô qua Bluetooth");
    }

    /// Tủ mà tài khoản đang đăng nhập được thử — rỗng ⇒ app ẩn nút Bluetooth ở trang hồ sơ.
    @GetMapping("/api/iot/ble-test/lockers")
    public ApiResponse<List<BleTestLockerResponse>> myLockers(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok(bleTest.lockersOf(userId));
    }

    @PostMapping("/api/iot/ble-test/lockers/{lockerId}/boxes/{boxNumber}/token")
    public ApiResponse<BleOpenTokenResponse> token(
            @PathVariable Long lockerId,
            @PathVariable Integer boxNumber,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok("BLE_TOKEN_ISSUED", "Đã cấp mã mở ô qua Bluetooth",
                bleTest.issueToken(lockerId, boxNumber, userId));
    }

    /// Khoá công khai Ed25519 để Pi kiểm mã — không bí mật, Pi tải qua HTTPS rồi giữ trong thẻ nhớ.
    @GetMapping("/api/iot/ble/public-key")
    public ApiResponse<Map<String, Object>> publicKey() {
        return ApiResponse.ok(bleTest.publicKey());
    }
}
