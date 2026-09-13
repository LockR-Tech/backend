package com.huynqb.laundrylocker.gateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class GatewayParityController {

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of("success", true, "data", "Welcome to Laundry Locker");
    }

    @GetMapping("/secured")
    public Map<String, Object> secured() {
        return Map.of("success", true, "data", "Secured endpoint");
    }

    @GetMapping("/api/admin/hello")
    public Map<String, Object> helloAdmin() {
        return Map.of("success", true, "data", "Hello Admin");
    }

    @GetMapping("/api/admin/system/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "api-gateway", "timestamp", Instant.now().toString());
    }
}
