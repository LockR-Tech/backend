package com.huynqb.laundrylocker.iot.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.iot.dto.BleOpenTokenResponse;
import com.huynqb.laundrylocker.iot.service.BleOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/// Mở ô theo đơn qua Bluetooth (F3-G11) — cần JWT (gateway thêm `X-User-Id`); quyền theo đơn do order-service
/// quyết định. Ô chỉ được coi là đã mở khi Pi báo về sự kiện `BOX_OPEN_BLE` có `nonce` của mã.
@RestController
@RequiredArgsConstructor
public class BleOrderController {

    private final BleOrderService bleOrders;

    @PostMapping("/api/iot/ble/orders/{orderId}/token")
    public ApiResponse<BleOpenTokenResponse> token(
            @PathVariable Long orderId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok("BLE_TOKEN_ISSUED", "Đã cấp mã mở ô qua Bluetooth",
                bleOrders.issueToken(orderId, userId));
    }
}
