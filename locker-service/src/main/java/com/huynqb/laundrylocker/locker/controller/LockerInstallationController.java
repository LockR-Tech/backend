package com.huynqb.laundrylocker.locker.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.security.UserRoles;
import com.huynqb.laundrylocker.locker.dto.InstallationCheckRequest;
import com.huynqb.laundrylocker.locker.dto.InstallationPhotosRequest;
import com.huynqb.laundrylocker.locker.dto.InstallationReasonRequest;
import com.huynqb.laundrylocker.locker.dto.InstallationRequest;
import com.huynqb.laundrylocker.locker.dto.InstallationResponse;
import com.huynqb.laundrylocker.locker.service.LockerInstallationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// Lắp đặt & nghiệm thu tủ (F3-G12). `/api/admin/**` chỉ ADMIN; `/api/locker-technician/**` cho KTV tủ
/// (ADMIN qua được mọi route) — service tự kiểm KTV có phải người được giao hồ sơ.
@RestController
@RequiredArgsConstructor
public class LockerInstallationController {

    private final LockerInstallationService installationService;

    // ─── Admin ────────────────────────────────────────────────────────────────────────────────────────────

    @GetMapping("/api/admin/lockers/installations")
    public ApiResponse<List<InstallationResponse>> listForAdmin(@RequestParam(required = false) String status) {
        return ApiResponse.ok(installationService.listForAdmin(status));
    }

    /// Hồ sơ mới nhất của tủ; data = null khi tủ chưa từng lập hồ sơ lắp đặt.
    @GetMapping("/api/admin/lockers/{lockerId}/installation")
    public ApiResponse<InstallationResponse> latestForLocker(@PathVariable Long lockerId) {
        return ApiResponse.ok(installationService.latestForLocker(lockerId));
    }

    @PostMapping("/api/admin/lockers/{lockerId}/installation")
    public ApiResponse<InstallationResponse> create(
            @PathVariable Long lockerId,
            @Valid @RequestBody InstallationRequest body,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok("INSTALLATION_CREATED", "Đã lập hồ sơ lắp đặt",
                installationService.create(lockerId, body, userId));
    }

    @GetMapping("/api/admin/lockers/installations/{id}")
    public ApiResponse<InstallationResponse> adminDetail(
            @PathVariable Long id, @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok(installationService.detail(id, userId, true));
    }

    @PutMapping("/api/admin/lockers/installations/{id}")
    public ApiResponse<InstallationResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody InstallationRequest body,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok("INSTALLATION_UPDATED", "Đã cập nhật hồ sơ lắp đặt",
                installationService.update(id, body, userId));
    }

    @PostMapping("/api/admin/lockers/installations/{id}/approve")
    public ApiResponse<InstallationResponse> approve(
            @PathVariable Long id, @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok("INSTALLATION_APPROVED", "Đã duyệt nghiệm thu, tủ bắt đầu hoạt động",
                installationService.approve(id, userId));
    }

    @PostMapping("/api/admin/lockers/installations/{id}/return")
    public ApiResponse<InstallationResponse> returnToTechnician(
            @PathVariable Long id,
            @Valid @RequestBody InstallationReasonRequest body,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok("INSTALLATION_RETURNED", "Đã trả hồ sơ về KTV",
                installationService.returnToTechnician(id, body.reason(), userId));
    }

    @PostMapping("/api/admin/lockers/installations/{id}/cancel")
    public ApiResponse<InstallationResponse> cancel(
            @PathVariable Long id,
            @Valid @RequestBody InstallationReasonRequest body,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok("INSTALLATION_CANCELLED", "Đã huỷ hồ sơ lắp đặt",
                installationService.cancel(id, body.reason(), userId));
    }

    @PostMapping("/api/admin/lockers/installations/{id}/photos")
    public ApiResponse<InstallationResponse> adminAddPhotos(
            @PathVariable Long id,
            @Valid @RequestBody InstallationPhotosRequest body,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok("INSTALLATION_PHOTOS_ADDED", "Đã thêm ảnh",
                installationService.addPhotos(id, body, userId, true));
    }

    @DeleteMapping("/api/admin/lockers/installations/{id}/photos/{photoId}")
    public ApiResponse<InstallationResponse> adminDeletePhoto(
            @PathVariable Long id, @PathVariable Long photoId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok("INSTALLATION_PHOTO_DELETED", "Đã xoá ảnh",
                installationService.deletePhoto(id, photoId, userId, true));
    }

    // ─── KTV lắp đặt ───────────────────────────────────────────────────────────────────────────────────────

    @GetMapping("/api/locker-technician/installations")
    public ApiResponse<List<InstallationResponse>> listForTechnician(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(installationService.listForTechnician(userId));
    }

    @GetMapping("/api/locker-technician/installations/{id}")
    public ApiResponse<InstallationResponse> technicianDetail(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok(installationService.detail(id, userId, UserRoles.isAdmin(roles)));
    }

    @PostMapping("/api/locker-technician/installations/{id}/start")
    public ApiResponse<InstallationResponse> start(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok("INSTALLATION_STARTED", "Đã bắt đầu lắp đặt",
                installationService.start(id, userId, UserRoles.isAdmin(roles)));
    }

    @PutMapping("/api/locker-technician/installations/{id}/checks/{item}")
    public ApiResponse<InstallationResponse> updateCheck(
            @PathVariable Long id, @PathVariable String item,
            @Valid @RequestBody InstallationCheckRequest body,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok("INSTALLATION_CHECK_UPDATED", "Đã cập nhật bước nghiệm thu",
                installationService.updateCheck(id, item, body, userId, UserRoles.isAdmin(roles)));
    }

    @PostMapping("/api/locker-technician/installations/{id}/photos")
    public ApiResponse<InstallationResponse> addPhotos(
            @PathVariable Long id,
            @Valid @RequestBody InstallationPhotosRequest body,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok("INSTALLATION_PHOTOS_ADDED", "Đã thêm ảnh",
                installationService.addPhotos(id, body, userId, UserRoles.isAdmin(roles)));
    }

    @DeleteMapping("/api/locker-technician/installations/{id}/photos/{photoId}")
    public ApiResponse<InstallationResponse> deletePhoto(
            @PathVariable Long id, @PathVariable Long photoId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok("INSTALLATION_PHOTO_DELETED", "Đã xoá ảnh",
                installationService.deletePhoto(id, photoId, userId, UserRoles.isAdmin(roles)));
    }

    @PostMapping("/api/locker-technician/installations/{id}/submit")
    public ApiResponse<InstallationResponse> submit(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok("INSTALLATION_SUBMITTED", "Đã gửi hồ sơ nghiệm thu",
                installationService.submit(id, userId, UserRoles.isAdmin(roles)));
    }
}
