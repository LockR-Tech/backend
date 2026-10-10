package com.huynqb.laundrylocker.order.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.security.UserRoles;
import com.huynqb.laundrylocker.order.dto.*;
import com.huynqb.laundrylocker.order.service.DroneParcelIncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class DroneParcelIncidentController {
    private final DroneParcelIncidentService service;

    @PostMapping("/internal/drone-incidents/{incidentCode}/return-flight")
    public ApiResponse<DroneParcelIncidentResponse> updateReturnFlight(
            @PathVariable String incidentCode,
            @Valid @RequestBody ReturnFlightUpdateRequest request) {
        return ApiResponse.ok("RETURN_FLIGHT_UPDATED", "Return-flight state updated",
                service.updateReturnFlight(incidentCode, request));
    }

    @PostMapping("/internal/drone-incidents/{incidentCode}/compensation")
    public ApiResponse<DroneParcelIncidentResponse> updateCompensation(
            @PathVariable String incidentCode,
            @Valid @RequestBody IncidentCompensationUpdateRequest request) {
        return ApiResponse.ok("COMPENSATION_UPDATED", "Compensation state updated",
                service.updateCompensation(incidentCode, request));
    }

    @GetMapping({
            "/api/drone-technician/drone-orders/{orderId}/camera",
            "/api/admin/drone-orders/{orderId}/camera"
    })
    public ApiResponse<Map<String, Object>> camera(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok(service.camera(orderId, userId, roles));
    }

    @PostMapping({
            "/api/drone-technician/drone-orders/{orderId}/drop-incident",
            "/api/admin/drone-orders/{orderId}/drop-incident"
    })
    public ApiResponse<DroneParcelIncidentResponse> report(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReportDroppedParcelRequest request) {
        return ApiResponse.ok(
                "DRONE_DROP_INCIDENT_REPORTED",
                "Dropped parcel incident reported",
                service.report(orderId, userId, roles, idempotencyKey, request));
    }

    @GetMapping("/api/admin/drone-incidents")
    public ApiResponse<List<DroneParcelIncidentResponse>> adminList(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        requireAdmin(roles);
        return ApiResponse.ok(service.list(userId, roles, false));
    }

    @GetMapping("/api/admin/drone-incidents/{id}")
    public ApiResponse<DroneParcelIncidentResponse> adminGet(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        requireAdmin(roles);
        return ApiResponse.ok(service.get(id, userId, roles));
    }

    @PostMapping("/api/admin/drone-incidents/{id}/recovery/verify")
    public ApiResponse<DroneParcelIncidentResponse> verifyRecovery(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        requireAdmin(roles);
        return ApiResponse.ok("RECOVERY_VERIFIED", "Recovery result verified", service.verifyRecovery(id, userId));
    }

    @PostMapping("/api/admin/drone-incidents/{id}/recovery/assign")
    public ApiResponse<DroneParcelIncidentResponse> assignRecovery(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @Valid @RequestBody AssignDroneRecoveryRequest request) {
        return ApiResponse.ok("RECOVERY_ASSIGNED", "Recovery technician assigned",
                service.assignRecovery(id, userId, roles, request));
    }

    @PostMapping("/api/admin/drone-incidents/{id}/proposals")
    public ApiResponse<DroneParcelIncidentResponse> propose(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @Valid @RequestBody DroneIncidentProposalRequest request) {
        requireAdmin(roles);
        return ApiResponse.ok("RESOLUTION_PROPOSED", "Resolution proposed", service.propose(id, userId, request));
    }

    @PostMapping("/api/admin/drone-incidents/{id}/compensation/approve")
    public ApiResponse<DroneParcelIncidentResponse> approveCompensation(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok("COMPENSATION_APPROVAL_PROCESSED",
                "Compensation approval processed", service.approveCompensation(id, userId, roles));
    }

    @GetMapping("/api/locker-technician/drone-recoveries")
    public ApiResponse<List<DroneParcelIncidentResponse>> recoveryQueue(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok(service.list(userId, roles, true));
    }

    @GetMapping("/api/locker-technician/drone-recoveries/{id}")
    public ApiResponse<DroneParcelIncidentResponse> recoveryDetail(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok(service.get(id, userId, roles));
    }

    @PostMapping("/api/locker-technician/drone-recoveries/{id}/actions")
    public ApiResponse<DroneParcelIncidentResponse> recoveryAction(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody DroneRecoveryUpdateRequest request) {
        return ApiResponse.ok("RECOVERY_UPDATED", "Recovery ticket updated", service.recoveryAction(id, userId, request));
    }

    @PostMapping("/api/locker-technician/drone-recoveries/{id}/submit")
    public ApiResponse<DroneParcelIncidentResponse> submitRecovery(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody DroneRecoverySubmitRequest request) {
        return ApiResponse.ok("RECOVERY_SUBMITTED", "Recovery result submitted", service.submitRecovery(id, userId, request));
    }

    @PostMapping("/api/locker-technician/drone-recoveries/{id}/hub-handover")
    public ApiResponse<DroneParcelIncidentResponse> hubHandover(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok("PARCEL_RETURNED_TO_HUB", "Parcel returned to Hub", service.confirmHubHandover(id, userId, roles));
    }

    @GetMapping("/api/orders/drone-incidents")
    public ApiResponse<List<DroneParcelIncidentResponse>> customerList(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok(service.list(userId, roles, false));
    }

    @GetMapping("/api/orders/drone-incidents/{id}")
    public ApiResponse<DroneParcelIncidentResponse> customerGet(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok(service.get(id, userId, roles));
    }

    @PostMapping("/api/orders/drone-incidents/{id}/response")
    public ApiResponse<DroneParcelIncidentResponse> customerRespond(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody DroneIncidentCustomerResponseRequest request) {
        return ApiResponse.ok("INCIDENT_RESPONSE_RECORDED", "Response recorded", service.customerRespond(id, userId, request));
    }

    private static void requireAdmin(String roles) {
        if (!UserRoles.isAdmin(roles)) {
            throw new BusinessException("ADMIN_REQUIRED", "Admin role is required", HttpStatus.FORBIDDEN);
        }
    }
}
