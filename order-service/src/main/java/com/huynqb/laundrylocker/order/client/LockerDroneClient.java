package com.huynqb.laundrylocker.order.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.dto.DroneStatusUpdateRequest;
import com.huynqb.laundrylocker.order.dto.DroneStatusTransitionRequest;
import com.huynqb.laundrylocker.order.dto.DroneTelemetryReport;
import com.huynqb.laundrylocker.order.dto.DroneUnitDto;
import com.huynqb.laundrylocker.order.dto.CreateIncidentTicketsCommand;
import com.huynqb.laundrylocker.order.dto.IncidentTicketBundle;
import com.huynqb.laundrylocker.order.dto.LockerLayoutDto;
import com.huynqb.laundrylocker.order.dto.IncidentInspectionStatus;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "locker-service", contextId = "lockerDroneClient")
public interface LockerDroneClient {

    @GetMapping("/internal/drones/{id}")
    ApiResponse<DroneUnitDto> getDroneUnit(@PathVariable Long id);

    /// Mọi drone (lọc theo tủ nếu có) — để chọn tủ gửi còn drone dùng được.
    @GetMapping("/internal/drones")
    ApiResponse<java.util.List<DroneUnitDto>> listDroneUnits(
            @org.springframework.web.bind.annotation.RequestParam(value = "lockerId", required = false) Long lockerId);

    /// Phiếu sự cố còn mở (OPEN/IN_PROGRESS) — để gắn đơn drone bị chặn với đúng phiếu.
    @GetMapping("/internal/reports/open")
    ApiResponse<java.util.List<com.huynqb.laundrylocker.order.dto.OpenReportDto>> openReports();

    @GetMapping("/internal/lockers/{id}/layout")
    ApiResponse<LockerLayoutDto> getLockerLayout(@PathVariable Long id);

    @PostMapping("/internal/drones/{id}/status")
    ApiResponse<DroneUnitDto> updateDroneStatus(
            @PathVariable Long id, @RequestBody DroneStatusUpdateRequest request);

    /// Pin drone tự báo qua telemetry; tra theo mã drone vì drone đang rảnh không có mission.
    @PostMapping("/internal/drones/telemetry")
    ApiResponse<DroneUnitDto> reportTelemetry(@RequestBody DroneTelemetryReport request);

    @PostMapping("/internal/drones/{id}/status-transition")
    ApiResponse<DroneUnitDto> transitionDroneStatus(
            @PathVariable Long id, @RequestBody DroneStatusTransitionRequest request);

    @PostMapping("/internal/drone-incidents/tickets")
    ApiResponse<IncidentTicketBundle> createIncidentTickets(@RequestBody CreateIncidentTicketsCommand request);

    @GetMapping("/internal/drone-incidents/inspection-reports/{id}")
    ApiResponse<IncidentInspectionStatus> getIncidentInspectionReport(@PathVariable Long id);

    @GetMapping("/internal/lockers/boxes/{boxId}/cell")
    ApiResponse<com.huynqb.laundrylocker.order.dto.BoxCellDto> getBoxCell(@PathVariable Long boxId);
}
