package com.huynqb.laundrylocker.store.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.store.dto.StoreRequest;
import com.huynqb.laundrylocker.store.dto.StoreResponse;
import com.huynqb.laundrylocker.store.service.StoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class StoreController {

    private final StoreService service;

    @PostMapping("/api/stores")
    public ApiResponse<StoreResponse> create(@Valid @RequestBody StoreRequest request) {
        return ApiResponse.ok("STORE_CREATED", "Store created", service.create(request));
    }

    @GetMapping("/api/stores")
    public ApiResponse<List<StoreResponse>> list(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double radiusKm) {
        if (latitude != null && longitude != null) {
            return ApiResponse.ok(service.nearby(latitude, longitude, radiusKm));
        }
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/api/stores/nearby")
    public ApiResponse<List<StoreResponse>> nearby(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(required = false) Double radiusKm) {
        return ApiResponse.ok(service.nearby(latitude, longitude, radiusKm));
    }

    @GetMapping("/api/stores/{id}")
    public ApiResponse<StoreResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/api/stores/{storeId}/ratings")
    public ApiResponse<List<Map<String, Object>>> ratings(@PathVariable Long storeId) {
        return ApiResponse.ok(service.ratings(storeId));
    }

    @GetMapping("/internal/stores/{id}")
    public ApiResponse<StoreResponse> getInternal(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PutMapping("/api/admin/stores/{id}")
    public ApiResponse<StoreResponse> adminUpdate(@PathVariable Long id, @Valid @RequestBody StoreRequest request) {
        return ApiResponse.ok("STORE_UPDATED", "Store updated", service.update(id, request));
    }

    @PostMapping("/api/admin/stores")
    public ApiResponse<StoreResponse> adminCreate(@Valid @RequestBody StoreRequest request) {
        return create(request);
    }

    @GetMapping("/api/admin/stores")
    public ApiResponse<List<StoreResponse>> adminList(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.adminList(search, status));
    }

    @GetMapping("/api/admin/stores/{id}")
    public ApiResponse<StoreResponse> adminGet(@PathVariable Long id) {
        return get(id);
    }

    @PutMapping("/api/admin/stores/{id}/status")
    public ApiResponse<StoreResponse> adminStatus(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestBody(required = false) Map<String, Object> request) {
        String resolved = status != null ? status : String.valueOf(request == null ? "ACTIVE" : request.get("status"));
        return ApiResponse.ok("STORE_STATUS_UPDATED", "Store status updated", service.updateStatus(id, resolved));
    }

    @PutMapping("/api/admin/stores/{id}/image")
    public ApiResponse<StoreResponse> adminImage(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        return ApiResponse.ok("STORE_IMAGE_UPDATED", "Store image updated", service.updateImage(id, String.valueOf(request.get("imageUrl"))));
    }

    @DeleteMapping("/api/admin/stores/{id}")
    public ApiResponse<Void> adminDelete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("STORE_DELETED", "Store deleted");
    }
}
