package com.huynqb.laundrylocker.order.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.order.dto.GuestNotification;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service", path = "/internal/notifications")
public interface NotificationClient {

    @PostMapping
    ApiResponse<Void> requestNotification(@RequestBody NotificationRequest request);

    /// Gửi mã mở tủ cho người nhận CHƯA có tài khoản, qua SMS và/hoặc email.
    @PostMapping("/guest")
    ApiResponse<GuestNotification.Result> notifyGuest(@RequestBody GuestNotification.Request request);
}
