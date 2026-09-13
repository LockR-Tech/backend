package com.huynqb.laundrylocker.locker.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.LockerBoxSummary;
import com.huynqb.laundrylocker.locker.dto.*;
import com.huynqb.laundrylocker.locker.service.LockerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class LockerController {

    private final LockerService lockerService;

    @PostMapping("/api/lockers")
    public ApiResponse<LockerResponse> createLocker(@Valid @RequestBody LockerRequest request) {
        return ApiResponse.ok("LOCKER_CREATED", "Locker created", lockerService.createLocker(request));
    }

    @GetMapping("/api/lockers")
    public ApiResponse<List<LockerResponse>> listLockers(@RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(lockerService.listLockers(storeId));
    }

    // Public endpoint for Kiosk to list lockers without token
    @GetMapping("/api/iot/lockers")
    public ApiResponse<List<LockerResponse>> listLockersForKiosk(@RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(lockerService.listLockers(storeId));
    }

    @GetMapping("/api/lockers/{id}")
    public ApiResponse<LockerResponse> getLocker(@PathVariable Long id) {
        return ApiResponse.ok(lockerService.getLocker(id));
    }

    // Public endpoint for Kiosk to get locker by id without token
    @GetMapping("/api/iot/lockers/{id}")
    public ApiResponse<LockerResponse> getLockerForKiosk(@PathVariable Long id) {
        return ApiResponse.ok(lockerService.getLocker(id));
    }

    @PostMapping("/api/boxes")
    public ApiResponse<LockerBoxSummary> createBox(@Valid @RequestBody BoxRequest request) {
        return ApiResponse.ok("BOX_CREATED", "Box created", lockerService.createBox(request));
    }

    @GetMapping("/api/lockers/{lockerId}/boxes")
    public ApiResponse<List<LockerBoxSummary>> listBoxes(@PathVariable Long lockerId) {
        return ApiResponse.ok(lockerService.listBoxes(lockerId));
    }

    @GetMapping("/api/lockers/{lockerId}/boxes/available")
    public ApiResponse<List<LockerBoxSummary>> listAvailableBoxes(@PathVariable Long lockerId) {
        return ApiResponse.ok(lockerService.listAvailableBoxes(lockerId));
    }

    @PostMapping("/api/boxes/{id}/open")
    public ApiResponse<LockerBoxSummary> openBox(@PathVariable Long id) {
        return ApiResponse.ok("BOX_OPENED", "Box open event published", lockerService.openBox(id));
    }

    @GetMapping("/internal/boxes/{id}")
    public ApiResponse<LockerBoxSummary> getBoxInternal(@PathVariable Long id) {
        return ApiResponse.ok(lockerService.getBox(id));
    }

    @PostMapping("/internal/boxes/{id}/reserve")
    public ApiResponse<LockerBoxSummary> reserveBox(
            @PathVariable Long id, @RequestParam(required = false, defaultValue = "CUSTOMER") String channel) {
        return ApiResponse.ok("BOX_RESERVED", "Box reserved", lockerService.reserveBox(id, channel));
    }

    @PostMapping("/internal/boxes/{id}/occupy")
    public ApiResponse<LockerBoxSummary> occupyBox(@PathVariable Long id) {
        return ApiResponse.ok("BOX_OCCUPIED", "Box occupied", lockerService.occupyBox(id));
    }

    @PostMapping("/internal/boxes/{id}/release")
    public ApiResponse<LockerBoxSummary> releaseBox(@PathVariable Long id) {
        return ApiResponse.ok("BOX_RELEASED", "Box released", lockerService.releaseBox(id));
    }

    @PostMapping("/internal/boxes/{id}/fault")
    public ApiResponse<CellResponse> markFaultInternal(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String reason = body == null ? null : body.get("reason");
        return ApiResponse.ok("BOX_FAULT_REPORTED", "Box marked as faulty", lockerService.markFault(id, reason, userId));
    }

    // Nguồn dữ liệu cho job đối soát ô ↔ đơn của order-service (Gap G4).
    @GetMapping("/internal/boxes")
    public ApiResponse<List<Map<String, Object>>> listBoxesInternal() {
        return ApiResponse.ok(lockerService.listBoxesForReconcile());
    }

    @GetMapping("/api/lockers/{id}/layout")
    public ApiResponse<LockerLayoutResponse> layout(@PathVariable Long id) {
        return ApiResponse.ok(lockerService.layout(id));
    }

    @GetMapping("/internal/lockers/{id}/layout")
    public ApiResponse<LockerLayoutResponse> layoutInternal(@PathVariable Long id) {
        return ApiResponse.ok(lockerService.layout(id));
    }

    @PostMapping("/api/boxes/{id}/fault")
    public ApiResponse<CellResponse> markFault(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String reason = body == null ? null : body.get("reason");
        return ApiResponse.ok("BOX_FAULT_REPORTED", "Box marked as faulty", lockerService.markFault(id, reason, userId));
    }

    @PostMapping("/api/admin/lockers/boxes/{id}/clear-fault")
    public ApiResponse<CellResponse> clearFault(@PathVariable Long id) {
        return ApiResponse.ok("BOX_FAULT_CLEARED", "Box fault cleared", lockerService.clearFault(id));
    }

    // ---- Admin ops (role ADMIN qua gateway; MANAGER/STAFF đã bỏ) ----

    @GetMapping("/api/admin/lockers/stats")
    public ApiResponse<List<LockerStatsResponse>> adminStats(@RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(lockerService.stats(storeId));
    }

    // ---- Maintenance (role MAINTENANCE/ADMIN qua gateway) ----

    @GetMapping("/api/maintenance/faults")
    public ApiResponse<List<FaultCellResponse>> maintenanceFaults() {
        return ApiResponse.ok(lockerService.openFaults());
    }

    @GetMapping("/api/maintenance/reports")
    public ApiResponse<List<LockerReportResponse>> maintenanceReports(
            @RequestParam(required = false, defaultValue = "false") boolean mine,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok(
                mine && userId != null ? lockerService.assignedReports(userId) : lockerService.openReports());
    }

    @PutMapping("/api/maintenance/reports/{id}/claim")
    public ApiResponse<LockerReportResponse> maintenanceClaim(
            @PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("REPORT_CLAIMED", "Report claimed", lockerService.claimReport(id, userId));
    }

    @PutMapping("/api/maintenance/reports/{id}/resolve")
    public ApiResponse<LockerReportResponse> maintenanceResolve(
            @PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(
                "REPORT_RESOLVED", "Report resolved and cell cleared", lockerService.resolveReportAndClearFault(id, userId));
    }

    @PostMapping("/api/maintenance/boxes/{id}/clear-fault")
    public ApiResponse<CellResponse> maintenanceClearFault(@PathVariable Long id) {
        return ApiResponse.ok("BOX_FAULT_CLEARED", "Box fault cleared", lockerService.clearFault(id));
    }

    @PostMapping("/api/maintenance/boxes/{id}/out-of-service")
    public ApiResponse<CellResponse> maintenanceOutOfService(
            @PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ApiResponse.ok(
                "BOX_OUT_OF_SERVICE", "Box taken out of service", lockerService.setOutOfService(id, reason));
    }

    @PostMapping("/api/maintenance/boxes/{id}/cleaning")
    public ApiResponse<CellResponse> maintenanceCleaning(@PathVariable Long id) {
        return ApiResponse.ok("BOX_CLEANING", "Box marked as cleaning", lockerService.setCleaning(id));
    }

    @PostMapping("/api/maintenance/boxes/{id}/return-to-service")
    public ApiResponse<CellResponse> maintenanceReturnToService(@PathVariable Long id) {
        return ApiResponse.ok(
                "BOX_RETURNED_TO_SERVICE", "Box returned to service", lockerService.returnToService(id));
    }

    // Emergency override: open a box without the customer's PIN/QR. Always
    // audited (credential type MASTER) by iot-service's box_access_logs.
    @PostMapping("/api/maintenance/boxes/{id}/force-open")
    public ApiResponse<Map<String, Object>> maintenanceForceOpen(
            @PathVariable Long id, @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok("BOX_FORCE_OPEN_ACCEPTED", "Force open accepted", lockerService.forceOpen(id, userId));
    }

    @GetMapping("/api/maintenance/my-rating-average")
    public ApiResponse<Map<String, Object>> maintenanceMyRatingAverage(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(lockerService.myRatingAverage(userId));
    }

    @GetMapping("/api/maintenance/reports/{id}/logs")
    public ApiResponse<List<RepairLogResponse>> maintenanceReportLogs(@PathVariable Long id) {
        return ApiResponse.ok(lockerService.repairLogs(id));
    }

    @PostMapping("/api/maintenance/reports/{id}/logs")
    public ApiResponse<RepairLogResponse> maintenanceAddReportLog(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok(
                "REPAIR_LOG_ADDED", "Repair log added",
                lockerService.addRepairLog(id, body.get("note"), userId));
    }

    // L5 — bảo trì phòng ngừa (lịch kiểm tra định kỳ)
    @GetMapping("/api/maintenance/schedules")
    public ApiResponse<List<MaintenanceScheduleResponse>> maintenanceSchedules() {
        return ApiResponse.ok(lockerService.listSchedules());
    }

    @PostMapping("/api/maintenance/schedules/{id}/complete")
    public ApiResponse<MaintenanceScheduleResponse> maintenanceCompleteSchedule(@PathVariable Long id) {
        return ApiResponse.ok(
                "SCHEDULE_COMPLETED", "Inspection completed", lockerService.completeSchedule(id));
    }

    @PostMapping("/api/admin/lockers/schedules")
    public ApiResponse<MaintenanceScheduleResponse> adminCreateSchedule(
            @Valid @RequestBody MaintenanceScheduleRequest request) {
        return ApiResponse.ok("SCHEDULE_CREATED", "Schedule created", lockerService.createSchedule(request));
    }

    @DeleteMapping("/api/admin/lockers/schedules/{id}")
    public ApiResponse<Void> adminDeleteSchedule(@PathVariable Long id) {
        lockerService.deleteSchedule(id);
        return ApiResponse.ok("SCHEDULE_DELETED", "Schedule deleted", null);
    }

    // ---- Drone fleet (role MAINTENANCE/ADMIN qua gateway) ----

    @GetMapping("/api/maintenance/drones")
    public ApiResponse<List<DroneUnitResponse>> maintenanceDrones() {
        return ApiResponse.ok(lockerService.listDroneUnits());
    }

    @GetMapping("/internal/drones/{id}")
    public ApiResponse<DroneUnitResponse> internalDrone(@PathVariable Long id) {
        return ApiResponse.ok(lockerService.getDroneUnit(id));
    }

    @PostMapping("/internal/drones/{id}/status")
    public ApiResponse<DroneUnitResponse> internalDroneStatus(
            @PathVariable Long id, @Valid @RequestBody DroneStatusRequest request) {
        return ApiResponse.ok(lockerService.updateDroneStatusInternal(id, request.status(), request.reason()));
    }

    @PostMapping("/api/maintenance/drones/{id}/claim")
    public ApiResponse<DroneUnitResponse> maintenanceClaimDrone(
            @PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("DRONE_CLAIMED", "Drone claimed", lockerService.claimDrone(id, userId));
    }

    @PostMapping("/api/maintenance/drones/{id}/release")
    public ApiResponse<DroneUnitResponse> maintenanceReleaseDrone(
            @PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("DRONE_RELEASED", "Drone released", lockerService.releaseDrone(id, userId));
    }

    @PostMapping("/api/maintenance/drones/{id}/status")
    public ApiResponse<DroneUnitResponse> maintenanceDroneStatus(
            @PathVariable Long id,
            @Valid @RequestBody DroneStatusRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(
                "DRONE_STATUS_UPDATED", "Drone status updated",
                lockerService.updateDroneStatus(id, request.status(), request.reason(), userId));
    }

    @PostMapping("/api/maintenance/drones/{id}/battery")
    public ApiResponse<DroneUnitResponse> maintenanceDroneBattery(
            @PathVariable Long id,
            @Valid @RequestBody DroneBatteryRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(
                "DRONE_BATTERY_UPDATED", "Drone battery updated",
                lockerService.updateDroneBattery(id, request.batteryPercent(), userId));
    }

    @GetMapping("/api/maintenance/drones/{id}/logs")
    public ApiResponse<List<DroneMaintenanceLogResponse>> maintenanceDroneLogs(@PathVariable Long id) {
        return ApiResponse.ok(lockerService.droneLogs(id));
    }

    @PostMapping("/api/maintenance/drones/{id}/logs")
    public ApiResponse<DroneMaintenanceLogResponse> maintenanceAddDroneLog(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(
                "DRONE_LOG_ADDED", "Drone log added",
                lockerService.addDroneLog(id, body.get("note"), userId));
    }

    @PostMapping("/api/admin/lockers/drones")
    public ApiResponse<DroneUnitResponse> adminCreateDrone(@Valid @RequestBody DroneUnitRequest request) {
        return ApiResponse.ok("DRONE_CREATED", "Drone created", lockerService.createDroneUnit(request));
    }

    @PutMapping("/api/admin/drones/{id}")
    public ApiResponse<DroneUnitResponse> adminUpdateDrone(
            @PathVariable Long id, @RequestBody DroneUpdateRequest request) {
        return ApiResponse.ok("DRONE_UPDATED", "Drone updated", lockerService.updateDroneUnit(id, request));
    }

    @DeleteMapping("/api/admin/drones/{id}")
    public ApiResponse<Void> adminDecommissionDrone(
            @PathVariable Long id, @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        lockerService.decommissionDrone(id, userId);
        return ApiResponse.ok("DRONE_DECOMMISSIONED", "Drone decommissioned", null);
    }

    // Maintenance box-health: logical (order-driven) status side-by-side with the
    // cabinet-reported hardware door state (iot-service, GAP 2), flagging doors open
    // on non-occupied boxes. Role MAINTENANCE/ADMIN (gateway-guarded /api/maintenance/**).
    @GetMapping("/api/maintenance/lockers/{lockerId}/box-health")
    public ApiResponse<List<BoxHealthResponse>> maintenanceBoxHealth(@PathVariable Long lockerId) {
        return ApiResponse.ok(lockerService.boxHealth(lockerId));
    }

    // Shift overview: every box across all lockers whose hardware door is OPEN while
    // not OCCUPIED (likely left ajar), with locker location for directions.
    @GetMapping("/api/maintenance/box-anomalies")
    public ApiResponse<List<BoxAnomalyResponse>> maintenanceBoxAnomalies() {
        return ApiResponse.ok(lockerService.boxAnomalies());
    }

    // #6 — bao tri bai dap drone (role MAINTENANCE/ADMIN)
    @PostMapping("/api/maintenance/lockers/{id}/landing-pad")
    public ApiResponse<LockerLayoutResponse> maintenanceLandingPadStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok(
                "LANDING_PAD_UPDATED", "Landing pad status updated",
                lockerService.updateLandingPadStatus(id, body.get("status"), body.get("reason"), userId));
    }

    @GetMapping("/api/admin/drones")
    public ApiResponse<List<DroneUnitResponse>> adminDrones(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long lockerId) {
        return ApiResponse.ok(lockerService.listDroneUnits(status, lockerId));
    }

    @GetMapping("/internal/lockers/{id}/boxes/find")
    public ApiResponse<CellResponse> findAvailable(
            @PathVariable Long id,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String cellType) {
        return ApiResponse.ok(lockerService.findAvailableBox(id, size, cellType));
    }

    @GetMapping("/internal/lockers/boxes/{boxId}/cell")
    public ApiResponse<CellResponse> getCellInternal(@PathVariable Long boxId) {
        return ApiResponse.ok(lockerService.getCell(boxId));
    }

    @PostMapping("/api/lockers/{id}/report")
    public ApiResponse<LockerReportResponse> report(@PathVariable Long id, @Valid @RequestBody LockerReportRequest request) {
        return ApiResponse.ok("LOCKER_REPORTED", "Locker report created", lockerService.report(id, request));
    }

    @GetMapping("/api/lockers/my-reports")
    public ApiResponse<List<LockerReportResponse>> myReports(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(lockerService.myReports(userId));
    }

    // Closes the loop the other way: customer rates how maintenance handled
    // their RESOLVED report. Owner-only, mirrors order-service's rate()/rating().
    @PostMapping("/api/lockers/reports/{id}/rate")
    public ApiResponse<LockerReportRatingResponse> rateReport(
            @PathVariable Long id,
            @Valid @RequestBody LockerReportRatingRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("REPORT_RATED", "Report rated", lockerService.rateReport(id, userId, request));
    }

    @GetMapping("/api/lockers/reports/{id}/rating")
    public ApiResponse<LockerReportRatingResponse> reportRating(@PathVariable Long id) {
        return ApiResponse.ok(lockerService.getReportRating(id));
    }

    @GetMapping("/api/admin/lockers/reports")
    public ApiResponse<List<LockerReportResponse>> adminReports(@RequestParam(required = false) Long userId) {
        return ApiResponse.ok(userId == null ? lockerService.allReports() : lockerService.myReports(userId));
    }

    @PutMapping("/api/admin/lockers/reports/{id}/resolve")
    public ApiResponse<LockerReportResponse> resolve(@PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("LOCKER_REPORT_RESOLVED", "Locker report resolved", lockerService.resolveReport(id, userId));
    }

    @GetMapping("/api/admin/lockers")
    public ApiResponse<List<LockerResponse>> adminList(@RequestParam(required = false) Long storeId) {
        return listLockers(storeId);
    }

    @PostMapping("/api/admin/lockers")
    public ApiResponse<LockerResponse> adminCreate(@Valid @RequestBody LockerRequest request) {
        return createLocker(request);
    }

    @GetMapping("/api/admin/lockers/{id}")
    public ApiResponse<LockerResponse> adminGet(@PathVariable Long id) {
        return getLocker(id);
    }

    @GetMapping("/api/admin/lockers/store/{storeId}")
    public ApiResponse<List<LockerResponse>> adminByStore(@PathVariable Long storeId) {
        return listLockers(storeId);
    }

    @PutMapping("/api/admin/lockers/{id}")
    public ApiResponse<LockerResponse> adminUpdate(@PathVariable Long id, @Valid @RequestBody LockerRequest request) {
        return ApiResponse.ok("LOCKER_UPDATED", "Locker updated", lockerService.updateLocker(id, request));
    }

    @DeleteMapping("/api/admin/lockers/{id}")
    public ApiResponse<Void> adminDelete(@PathVariable Long id) {
        lockerService.deleteLocker(id);
        return ApiResponse.ok("LOCKER_DELETED", "Locker deleted");
    }

    @PutMapping("/api/admin/lockers/{id}/maintenance")
    public ApiResponse<LockerResponse> maintenance(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        boolean maintenance = Boolean.parseBoolean(String.valueOf(request.getOrDefault("maintenance", "true")));
        return ApiResponse.ok("LOCKER_MAINTENANCE_UPDATED", "Locker maintenance updated", lockerService.setMaintenance(id, maintenance));
    }

    @PostMapping("/api/admin/lockers/{id}/boxes")
    public ApiResponse<LockerBoxSummary> adminAddBox(@PathVariable Long id, @Valid @RequestBody BoxRequest request) {
        return createBox(new BoxRequest(
                id, request.boxNumber(), request.size(), request.status(),
                request.cellType(), request.rowIndex(), request.colIndex()));
    }

    @PutMapping("/api/admin/lockers/boxes/{boxId}/status")
    public ApiResponse<LockerBoxSummary> adminBoxStatus(@PathVariable Long boxId, @RequestBody Map<String, Object> request) {
        return ApiResponse.ok("BOX_STATUS_UPDATED", "Box status updated", lockerService.updateBoxStatus(boxId, String.valueOf(request.get("status"))));
    }
}
