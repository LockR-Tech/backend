package com.huynqb.laundrylocker.user.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "notification-service")
public interface NotificationClient {

    @PostMapping("/internal/fcm-tokens")
    ApiResponse<Void> saveFcmToken(@RequestBody Map<String, Object> request);

    @DeleteMapping("/internal/fcm-tokens")
    ApiResponse<Void> deleteFcmToken(@RequestBody Map<String, Object> request);
}
