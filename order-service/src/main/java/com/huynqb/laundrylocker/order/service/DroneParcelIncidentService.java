package com.huynqb.laundrylocker.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.common.media.MediaPurpose;
import com.huynqb.laundrylocker.common.media.VerifiedMedia;
import com.huynqb.laundrylocker.common.security.UserRoles;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.*;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.*;
import com.huynqb.laundrylocker.order.repository.*;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@lombok.extern.slf4j.Slf4j
@Service
@RequiredArgsConstructor
public class DroneParcelIncidentService {
    static final double FPT_HCM_LATITUDE = 10.841100;
    static final double FPT_HCM_LONGITUDE = 106.809700;
    private static final Set<String> REPORTABLE_MISSION_STATES = Set.of(
            "LAUNCHING", "DEPARTED", "EN_ROUTE", "APPROACHING", "ARRIVED");
    private static final Set<String> RECOVERY_ACTIONS = Set.of("ACCEPT", "START_SEARCHING");
    private static final Set<String> RECOVERY_OUTCOMES = Set.of("FOUND", "NOT_FOUND", "UNSAFE");
    private static final Set<String> RESOLUTION_TYPES = Set.of(
            "FREE_REDELIVERY", "REDELIVERY_PARTIAL_COMPENSATION", "COMPENSATION_ONLY", "MANUAL_RESOLUTION");

    private final DroneParcelIncidentRepository incidentRepository;
    private final DroneIncidentEvidenceRepository evidenceRepository;
    private final DroneIncidentTimelineRepository timelineRepository;
    private final DroneIncidentResolutionProposalRepository proposalRepository;
    private final LockerOrderRepository orderRepository;
    private final DroneMissionRepository missionRepository;
    private final OrderStatusHistoryRepository orderHistoryRepository;
    private final DroneTelemetryRegistry telemetryRegistry;
    private final LockerDroneClient lockerDroneClient;
    private final LockerLookupClient lockerLookupClient;
    private final UserClient userClient;
    private final NotificationClient notificationClient;
    private final DroneReturnCommandGateway returnCommandGateway;
    private final IncidentCompensationGateway compensationGateway;
    private final IncidentRedeliveryGateway redeliveryGateway;
    private final DronePickupService dronePickupService;
    private final CloudinaryMediaStorage mediaStorage;
    private final ObjectMapper objectMapper;
    private final OrderRules rules;
    private final LockerClient lockerClient;

    @Value("${app.drone.camera.stream-url-template:}")
    private String cameraStreamUrlTemplate;

    @Transactional
    public DroneParcelIncidentResponse report(
            Long orderId,
            Long actorUserId,
            String roles,
            String idempotencyKey,
            ReportDroppedParcelRequest request) {
        requireIdempotencyKey(idempotencyKey);
        DroneParcelIncident replay = incidentRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (replay != null) {
            if (!orderId.equals(replay.getOrderId())) {
                throw new BusinessException(
                        "IDEMPOTENCY_KEY_REUSED",
                        "The idempotency key was already used for another order",
                        HttpStatus.CONFLICT);
            }
            assertCanView(replay, actorUserId, roles);
            return toResponse(replay);
        }

        LockerOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("LockerOrder", orderId));
        if (!"DRONE_DELIVERY".equalsIgnoreCase(order.getType())) {
            throw new BusinessException("DRONE_ORDER_REQUIRED", "Dropped parcel incidents require a drone order");
        }
        DroneMission mission = missionRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException("DRONE_MISSION_NOT_FOUND", "Drone mission not found"));
        assertReporter(actorUserId, roles, mission);
        if (!REPORTABLE_MISSION_STATES.contains(mission.getStatus())) {
            throw new BusinessException(
                    "DRONE_DROP_NOT_REPORTABLE",
                    "A dropped parcel can only be reported while the mission is active",
                    HttpStatus.CONFLICT);
        }
        DroneParcelIncident existing = incidentRepository.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            throw new BusinessException(
                    "DRONE_DROP_INCIDENT_EXISTS",
                    "This journey already has incident " + existing.getIncidentCode(),
                    HttpStatus.CONFLICT);
        }

        DroneTelemetryFrame telemetry = telemetryRegistry.latest(mission.getDroneCode()).orElse(null);
        boolean telemetryLive = telemetryRegistry.isLive(mission.getDroneCode());
        Location location = incidentLocation(request, telemetry, telemetryLive, order, mission);
        VerifiedMedia snapshot = request.cameraSnapshot() == null
                ? null
                : mediaStorage.verify(request.cameraSnapshot(), MediaPurpose.REPORT_EVIDENCE, actorUserId);

        DroneParcelIncident incident = new DroneParcelIncident();
        incident.setIncidentCode("DPI-" + orderId + "-" + System.currentTimeMillis());
        incident.setOrderId(orderId);
        incident.setMissionId(mission.getId());
        incident.setDroneUnitId(mission.getDroneUnitId());
        incident.setDroneCode(mission.getDroneCode());
        incident.setReportedByUserId(actorUserId);
        incident.setIdempotencyKey(idempotencyKey.trim());
        incident.setReason(request.reason().trim());
        incident.setStatus("REPORTED");
        incident.setParcelStatus("DROP_REPORTED");
        incident.setRecoveryStatus("OPEN");
        incident.setInspectionStatus("OPEN");
        incident.setRedeliveryStatus("NOT_REQUIRED");
        incident.setCompensationStatus("NOT_REQUIRED");
        incident.setReturnFlightStatus("RETURN_REQUESTED");
        incident.setDropLatitude(location.latitude());
        incident.setDropLongitude(location.longitude());
        incident.setGpsAccuracyM(location.accuracyM());
        incident.setGpsSource(location.source());
        incident.setTelemetryStale(!telemetryLive);
        incident.setTelemetryObservedAt(telemetry == null || telemetry.observedAtMs() == null
                ? null : Instant.ofEpochMilli(telemetry.observedAtMs()));
        incident.setTelemetryJson(json(telemetry));
        incident.setCameraStatus(normalizeCameraStatus(request.cameraStatus(), snapshot));
        incident.setCameraSnapshotUrl(snapshot == null ? null : snapshot.secureUrl());
        incident = incidentRepository.saveAndFlush(incident);

        if (snapshot != null) {
            saveEvidence(incident.getId(), "REPORT", snapshot, "Ảnh camera tại thời điểm báo rơi",
                    location.latitude(), location.longitude(), location.accuracyM(),
                    parseInstant(request.snapshotCapturedAt()), actorUserId);
        }

        // Stop the delivery state machine before any external action. It can no
        // longer advance to DEPOSITED/DELIVERED from telemetry or operator input.
        String oldMissionStatus = mission.getStatus();
        mission.setStatus("DROP_REPORTED");
        mission.setEndedAt(LocalDateTime.now());
        mission.setEndedByUserId(actorUserId);
        mission.setEndNote("Parcel drop incident " + incident.getIncidentCode());
        mission.setFailedStage(oldMissionStatus);
        missionRepository.save(mission);
        String oldOrderStage = order.getDeliveryStage();
        order.setDeliveryStage("DROP_REPORTED");
        orderRepository.save(order);
        addOrderHistory(orderId, oldOrderStage, "DROP_REPORTED", actorUserId,
                "Reported dropped parcel: " + incident.getIncidentCode());

        try {
            lockerDroneClient.transitionDroneStatus(
                    mission.getDroneUnitId(),
                    new DroneStatusTransitionRequest(
                            "IN_FLIGHT", "FAULT", "Parcel drop incident " + incident.getIncidentCode(), false));
        } catch (Exception ignored) {
            // Fleet state may already be FAULT/offline. The incident remains the
            // source of truth and the drone is never set back to IDLE here.
        }

        IncidentTicketBundle tickets = lockerDroneClient.createIncidentTickets(new CreateIncidentTicketsCommand(
                incident.getId(), incident.getIncidentCode(), order.getId(), order.getOrderCode(),
                mission.getDroneUnitId(), mission.getDroneCode(), actorUserId, request.reason(),
                location.latitude(), location.longitude(), location.accuracyM(), incident.getCameraSnapshotUrl())).data();
        if (tickets != null) {
            incident.setInspectionReportId(tickets.inspectionReportId());
            incident.setInspectionStatus(tickets.inspectionTechnicianId() == null ? "AWAITING_ASSIGNMENT" : "ASSIGNED");
            incident.setRecoveryAssignedToUserId(tickets.recoveryTechnicianId());
            incident.setRecoveryLockerId(tickets.recoveryLockerId());
            incident.setRecoveryStatus(tickets.recoveryTechnicianId() == null ? "AWAITING_ASSIGNMENT" : "ASSIGNED");
            incident.setParcelStatus("RECOVERY_PENDING");
        }

        DroneReturnCommandGateway.CommandResult rtl = canRequestReturn(telemetry, telemetryLive)
                ? returnCommandGateway.requestReturnToLaunch(mission.getDroneCode(), incident.getIncidentCode())
                : new DroneReturnCommandGateway.CommandResult(false, "Telemetry is stale or flight safety is unknown");
        incident.setReturnFlightStatus(rtl.requested()
                ? "RETURN_REQUESTED" : "MANUAL_INTERVENTION_REQUIRED");
        incident.setStatus(rtl.requested() ? "INVESTIGATING" : "MANUAL_INTERVENTION_REQUIRED");
        incident = incidentRepository.save(incident);
        timeline(incident, "INCIDENT_REPORTED", null, incident.getStatus(), actorUserId, request.reason(), null);
        timeline(incident, "RETURN_REQUEST", null, incident.getReturnFlightStatus(), actorUserId, rtl.detail(), null);
        notifyIncidentCreatedQuietly(incident, order, tickets);
        return toResponse(incident);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> camera(Long orderId, Long actorUserId, String roles) {
        LockerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("LockerOrder", orderId));
        DroneMission mission = missionRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException("DRONE_MISSION_NOT_FOUND", "Drone mission not found"));
        boolean assignedDroneTechnician = UserRoles.parse(roles).contains("DRONE_TECHNICIAN")
                && actorUserId.equals(mission.getAssignedByUserId());
        if (!UserRoles.isAdmin(roles) && !assignedDroneTechnician) {
            throw new BusinessException("DRONE_JOURNEY_FORBIDDEN", "Only the assigned technician or Admin may view camera", HttpStatus.FORBIDDEN);
        }
        DroneTelemetryFrame frame = telemetryRegistry.latest(mission.getDroneCode()).orElse(null);
        boolean telemetryLive = telemetryRegistry.isLive(mission.getDroneCode());
        // Số đo bay luôn đi kèm, kể cả khi chưa cấu hình luồng camera: KTV vẫn cần pin, chế độ bay
        // và toạ độ để quyết định báo rơi kiện. Giữ quy ước cũ: -1 / "UNKNOWN" / 0 / "" khi thiếu.
        Map<String, Object> result = new HashMap<>();
        result.put("droneCode", mission.getDroneCode());
        result.put("journeyStatus", String.valueOf(order.getDeliveryStage()));
        result.put("telemetryLive", telemetryLive);
        result.put("telemetryAvailable", frame != null);
        boolean simulatedLocation = !telemetryLive
                && "DEMO".equalsIgnoreCase(order.getFulfillmentMode());
        result.put("batteryPercent", frame == null || frame.batteryPercent() == null ? -1 : frame.batteryPercent());
        result.put("flightMode", frame == null || frame.flight() == null || frame.flight().mode() == null
                ? "UNKNOWN" : frame.flight().mode());
        double[] routePos = calculateRouteCoordinates(order, mission);
        double fallbackLat = routePos != null ? routePos[0] : FPT_HCM_LATITUDE;
        double fallbackLng = routePos != null ? routePos[1] : FPT_HCM_LONGITUDE;
        result.put("latitude", frame != null && frame.hasPosition()
                ? frame.lat() : simulatedLocation ? fallbackLat : 0D);
        result.put("longitude", frame != null && frame.hasPosition()
                ? frame.lng() : simulatedLocation ? fallbackLng : 0D);
        result.put("locationSource", frame != null && frame.hasPosition()
                ? "TELEMETRY" : simulatedLocation ? "FLIGHT_PATH" : "UNAVAILABLE");
        result.put("observedAt", frame == null || frame.observedAt() == null ? "" : frame.observedAt());
        result.put("gpsFix", frame != null && frame.hasPosition());
        result.put("gpsFixType", frame == null || frame.gps() == null ? null : frame.gps().fixType());
        result.put("satellites", frame == null || frame.gps() == null ? null : frame.gps().satellites());
        result.put("altitudeM", frame == null || frame.position() == null ? null : frame.position().relativeAltM());
        result.put("headingDeg", frame == null ? null : frame.headingDeg());
        result.put("groundSpeedMs", frame == null ? null : frame.groundSpeedMs());
        if (!StringUtils.hasText(cameraStreamUrlTemplate)) {
            result.put("status", "UNAVAILABLE");
            result.put("integrationAvailable", false);
            return result;
        }
        result.put("status", "CONNECTING");
        result.put("integrationAvailable", true);
        result.put("streamType", "HLS");
        result.put("streamUrl", cameraStreamUrlTemplate.replace("{droneCode}", mission.getDroneCode()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<DroneParcelIncidentResponse> list(Long actorUserId, String roles, boolean recoveryQueue) {
        List<String> parsedRoles = UserRoles.parse(roles);
        List<DroneParcelIncident> incidents;
        if (UserRoles.isAdmin(roles)) {
            incidents = incidentRepository.findAllByOrderByUpdatedAtDesc();
        } else if (recoveryQueue || parsedRoles.contains("LOCKER_TECHNICIAN")) {
            incidents = incidentRepository.findByRecoveryAssignedToUserIdOrderByUpdatedAtDesc(actorUserId);
        } else if (parsedRoles.contains("DRONE_TECHNICIAN")) {
            incidents = incidentRepository.findByReportedByUserIdOrRecoveryAssignedToUserIdOrderByUpdatedAtDesc(
                    actorUserId, actorUserId);
        } else {
            incidents = incidentRepository.findAllByOrderByUpdatedAtDesc().stream()
                    .filter(incident -> {
                        LockerOrder order = orderRepository.findById(incident.getOrderId()).orElse(null);
                        return order != null && (actorUserId.equals(order.getUserId())
                                || actorUserId.equals(order.getReceiverUserId()));
                    })
                    .toList();
        }
        return incidents.stream().map(this::toResponse).toList();
    }

    /// Danh sách sự cố cho app KTV drone: sự cố mình báo, mình được giao thu hồi, hoặc xảy ra trên
    /// chuyến bay mình đã tiếp nhận (kể cả khi admin báo thay). Chỉ vai trò DRONE_TECHNICIAN.
    @Transactional(readOnly = true)
    public List<DroneParcelIncidentResponse> listForDroneTechnician(Long actorUserId, String roles) {
        requireDroneTechnician(roles);
        return incidentRepository.findVisibleToDroneTechnician(actorUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DroneParcelIncidentResponse getForDroneTechnician(Long incidentId, Long actorUserId, String roles) {
        requireDroneTechnician(roles);
        DroneParcelIncident incident = find(incidentId);
        boolean involved = actorUserId.equals(incident.getReportedByUserId())
                || actorUserId.equals(incident.getRecoveryAssignedToUserId())
                || (incident.getMissionId() != null && missionRepository.findById(incident.getMissionId())
                        .map(mission -> actorUserId.equals(mission.getAssignedByUserId()))
                        .orElse(false));
        if (!involved) {
            throw new BusinessException(
                    "INCIDENT_FORBIDDEN", "Sự cố này không thuộc chuyến bay của bạn", HttpStatus.FORBIDDEN);
        }
        return toResponse(incident);
    }

    @Transactional(readOnly = true)
    public DroneParcelIncidentResponse get(Long incidentId, Long actorUserId, String roles) {
        DroneParcelIncident incident = find(incidentId);
        assertCanView(incident, actorUserId, roles);
        return toResponse(incident);
    }

    @Transactional
    public DroneParcelIncidentResponse recoveryAction(
            Long incidentId, Long actorUserId, DroneRecoveryUpdateRequest request) {
        DroneParcelIncident incident = locked(incidentId);
        assertRecoveryAssignee(incident, actorUserId);
        String action = request.action().trim().toUpperCase(Locale.ROOT);
        if (!RECOVERY_ACTIONS.contains(action)) {
            throw new BusinessException("RECOVERY_ACTION_INVALID", "Unsupported recovery action");
        }
        String previous = incident.getRecoveryStatus();
        if ("ACCEPT".equals(action)) {
            requireState(previous, Set.of("ASSIGNED"), "RECOVERY_NOT_ACCEPTABLE");
            incident.setRecoveryStatus("ACCEPTED");
        } else {
            requireState(previous, Set.of("ACCEPTED"), "RECOVERY_NOT_STARTABLE");
            incident.setRecoveryStatus("SEARCHING");
            incident.setRecoveryStartedAt(LocalDateTime.now());
            incident.setStatus("RECOVERY_IN_PROGRESS");
        }
        incidentRepository.save(incident);
        timeline(incident, "RECOVERY_" + action, previous, incident.getRecoveryStatus(), actorUserId, null, null);
        return toResponse(incident);
    }

    @Transactional
    public DroneParcelIncidentResponse submitRecovery(
            Long incidentId, Long actorUserId, DroneRecoverySubmitRequest request) {
        DroneParcelIncident incident = locked(incidentId);
        assertRecoveryAssignee(incident, actorUserId);
        requireState(incident.getRecoveryStatus(), Set.of("SEARCHING"), "RECOVERY_NOT_SEARCHING");
        String outcome = request.outcome().trim().toUpperCase(Locale.ROOT);
        if (!RECOVERY_OUTCOMES.contains(outcome)) {
            throw new BusinessException("RECOVERY_OUTCOME_INVALID", "Outcome must be FOUND, NOT_FOUND or UNSAFE");
        }
        String condition = request.parcelCondition() == null
                ? "UNKNOWN" : request.parcelCondition().trim().toUpperCase(Locale.ROOT);
        if ("FOUND".equals(outcome) && !DroneIncidentStates.CONDITIONS.contains(condition)) {
            throw new BusinessException("PARCEL_CONDITION_INVALID", "Unknown parcel condition");
        }
        if (request.evidence() == null || request.evidence().isEmpty()) {
            throw new BusinessException("RECOVERY_EVIDENCE_REQUIRED", "Recovery result requires at least one photo");
        }
        for (DroneIncidentEvidenceRequest item : request.evidence()) {
            VerifiedMedia media = mediaStorage.verify(item.media(), MediaPurpose.REPORT_EVIDENCE, actorUserId);
            saveEvidence(incidentId, "RECOVERY", media, item.caption(), item.latitude(), item.longitude(),
                    item.gpsAccuracyM(), parseInstant(item.capturedAt()), actorUserId);
        }
        incident.setRecoveryOutcome(outcome);
        incident.setParcelCondition(condition);
        incident.setRecoveryNote(trim(request.note()));
        incident.setRecoveredLatitude(request.latitude());
        incident.setRecoveredLongitude(request.longitude());
        incident.setRecoveredGpsAccuracyM(request.gpsAccuracyM());
        incident.setRecoverySubmittedAt(LocalDateTime.now());
        incident.setRecoveryStatus("SUBMITTED");
        incident.setParcelStatus("FOUND".equals(outcome)
                ? (Set.of("MINOR_DAMAGE", "MAJOR_DAMAGE", "UNUSABLE").contains(condition) ? "DAMAGED" : "FOUND")
                : "NOT_FOUND".equals(outcome) ? "LOST" : "RECOVERY_PENDING");
        incident.setStatus("AWAITING_ADMIN_REVIEW");
        incidentRepository.save(incident);
        timeline(incident, "RECOVERY_SUBMITTED", "SEARCHING", "SUBMITTED", actorUserId, request.note(), null);
        return toResponse(incident);
    }

    @Transactional
    public DroneParcelIncidentResponse verifyRecovery(Long incidentId, Long adminUserId) {
        DroneParcelIncident incident = locked(incidentId);
        requireState(incident.getRecoveryStatus(), Set.of("SUBMITTED"), "RECOVERY_NOT_SUBMITTED");
        incident.setRecoveryStatus("VERIFIED");
        incident.setRecoveryVerifiedAt(LocalDateTime.now());
        incident.setStatus("AWAITING_ADMIN_REVIEW");
        incidentRepository.save(incident);
        timeline(incident, "RECOVERY_VERIFIED", "SUBMITTED", "VERIFIED", adminUserId, null, null);
        return toResponse(incident);
    }

    @Transactional
    public DroneParcelIncidentResponse assignRecovery(
            Long incidentId, Long adminUserId, String roles, AssignDroneRecoveryRequest request) {
        requireAdmin(roles);
        DroneParcelIncident incident = locked(incidentId);
        requireState(incident.getRecoveryStatus(), Set.of("OPEN", "AWAITING_ASSIGNMENT", "ASSIGNED"),
                "RECOVERY_NOT_ASSIGNABLE");
        boolean isLockerTech = false;
        boolean isDroneTech = false;
        try {
            var user = userClient.getUser(request.technicianId()).data();
            if (user != null && "ACTIVE".equalsIgnoreCase(user.status()) && user.roles() != null) {
                isLockerTech = user.roles().contains("LOCKER_TECHNICIAN");
                isDroneTech = user.roles().contains("DRONE_TECHNICIAN");
            }
        } catch (RuntimeException error) {
            throw new BusinessException("TECHNICIAN_DIRECTORY_UNAVAILABLE",
                    "Không thể kiểm tra thông tin KTV được chọn", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!isLockerTech && !isDroneTech) {
            throw new BusinessException("RECOVERY_TECHNICIAN_INVALID",
                    "Người dùng được chọn không phải là KTV tủ hoặc KTV drone đang hoạt động");
        }
        if (isDroneTech && dronePickupService.busy(request.technicianId())) {
            throw new BusinessException("DRONE_TECHNICIAN_BUSY",
                    "KTV drone này đang có đơn hàng hoặc chuyến bay đang vận hành, không thể nhận nhiệm vụ tìm hàng");
        }
        Long previousAssignee = incident.getRecoveryAssignedToUserId();
        incident.setRecoveryAssignedToUserId(request.technicianId());
        incident.setRecoveryLockerId(request.responsibilityLockerId());
        incident.setRecoveryStatus("ASSIGNED");
        incidentRepository.save(incident);
        timeline(incident, "RECOVERY_ASSIGNED", null, "ASSIGNED", adminUserId, request.note(),
                json(Map.of("previousAssigneeId", previousAssignee == null ? "" : previousAssignee,
                        "technicianId", request.technicianId())));
        notifyQuietly(request.technicianId(), "Nhiệm vụ thu hồi kiện drone",
                "Bạn được phân công thu hồi kiện cho sự cố " + incident.getIncidentCode() + ".",
                "DRONE_PARCEL_RECOVERY_ASSIGNED", incident.getId());
        return toResponse(incident);
    }

    @Transactional
    public DroneParcelIncidentResponse completeRedelivery(Long incidentId, Long adminUserId, String roles) {
        requireAdmin(roles);
        DroneParcelIncident incident = locked(incidentId);
        requireState(incident.getRedeliveryStatus(), Set.of("PREPARING", "IN_PROGRESS"), "REDELIVERY_NOT_IN_PROGRESS");
        markRedeliveryCompleted(incident, adminUserId, "Admin xác nhận đã giao lại hàng thành công cho khách hàng");
        return toResponse(incident);
    }

    /// Đơn giao lại của một sự cố vừa hoàn tất (khách lấy hàng) ⇒ đóng phần giao lại của sự cố. Trước đây
    /// sự cố đứng ở "đang chuẩn bị giao lại" cho tới khi admin bấm tay (test 2026-10-29). Không phải đơn giao
    /// lại ⇒ không làm gì. Gọi sau commit của đơn (afterCommit) nên cần giao dịch mới.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onRedeliveryOrderCompleted(Long redeliveryOrderId) {
        incidentRepository.findFirstByRedeliveryOrderId(redeliveryOrderId).ifPresent(found -> {
            DroneParcelIncident incident = locked(found.getId());
            if (!Set.of("PREPARING", "IN_PROGRESS").contains(incident.getRedeliveryStatus())) {
                return;
            }
            markRedeliveryCompleted(incident, null,
                    "Khách đã nhận hàng của đơn giao lại #" + redeliveryOrderId);
        });
    }

    private void markRedeliveryCompleted(DroneParcelIncident incident, Long actorUserId, String note) {
        String previous = incident.getRedeliveryStatus();
        incident.setRedeliveryStatus("COMPLETED");
        incident.setParcelStatus("REDELIVERED");
        incidentRepository.save(incident);
        timeline(incident, "REDELIVERY_COMPLETED", previous, "COMPLETED", actorUserId, note, null);
        resolveIfSettled(incident, actorUserId);
    }

    /// Sự cố đã chọn phương án (khách đồng ý) và không còn phần giao lại / chi trả nào dở ⇒ RESOLVED.
    /// Gom một chỗ để mọi nhánh (giao lại xong, chi trả xong, thoả thuận riêng không còn việc) đóng được sự
    /// cố — trước đây thiếu nhánh nên sự cố kẹt RESOLUTION_IN_PROGRESS vĩnh viễn.
    private void resolveIfSettled(DroneParcelIncident incident, Long actorUserId) {
        if (!"RESOLUTION_IN_PROGRESS".equals(incident.getStatus())) {
            return;
        }
        boolean redeliveryOpen = Set.of("AWAITING_CUSTOMER_ACCEPTANCE", "ACCEPTED", "PREPARING", "IN_PROGRESS")
                .contains(incident.getRedeliveryStatus());
        boolean compensationOpen = Set.of("AWAITING_CUSTOMER_ACCEPTANCE", "ACCEPTED", "AWAITING_APPROVAL",
                        "PAYMENT_PENDING", "PROCESSING", "PAYMENT_FAILED", "DISPUTED")
                .contains(incident.getCompensationStatus());
        if (redeliveryOpen || compensationOpen) {
            return;
        }
        incident.setStatus("RESOLVED");
        incident.setResolvedAt(LocalDateTime.now());
        if ("PAID".equals(incident.getCompensationStatus()) && !"REDELIVERED".equals(incident.getParcelStatus())) {
            incident.setParcelStatus("COMPENSATED");
        }
        incidentRepository.save(incident);
        timeline(incident, "INCIDENT_RESOLVED", "RESOLUTION_IN_PROGRESS", "RESOLVED", actorUserId,
                "Đã hoàn tất phương án xử lý", null);
    }

    @Transactional
    public DroneParcelIncidentResponse confirmHubHandover(Long incidentId, Long actorUserId, String roles) {
        DroneParcelIncident incident = locked(incidentId);
        if (!UserRoles.isAdmin(roles) && !actorUserId.equals(incident.getRecoveryAssignedToUserId())) {
            throw new BusinessException("RECOVERY_FORBIDDEN", "Only the assigned technician or Admin may confirm handover", HttpStatus.FORBIDDEN);
        }
        requireState(incident.getRecoveryStatus(), Set.of("VERIFIED"), "RECOVERY_NOT_VERIFIED");
        if (!"FOUND".equals(incident.getRecoveryOutcome())) {
            throw new BusinessException("PARCEL_NOT_FOUND", "Only a found parcel can be handed over to Hub");
        }
        incident.setReturnedToHubAt(LocalDateTime.now());
        incident.setReturnedToHubByUserId(actorUserId);
        incident.setRecoveryStatus("CLOSED");
        incident.setParcelStatus("RETURNED_TO_HUB");
        incidentRepository.save(incident);
        timeline(incident, "PARCEL_RETURNED_TO_HUB", "VERIFIED", "CLOSED", actorUserId, null, null);
        return toResponse(incident);
    }

    @Transactional
    public DroneParcelIncidentResponse updateReturnFlight(
            String incidentCode, ReturnFlightUpdateRequest request) {
        DroneParcelIncident incident = incidentRepository.findByIncidentCode(incidentCode)
                .orElseThrow(() -> new BusinessException("DRONE_INCIDENT_NOT_FOUND", "Incident not found"));
        String next = request.status().trim().toUpperCase(Locale.ROOT);
        if (!DroneIncidentStates.RETURN_FLIGHT.contains(next)) {
            throw new BusinessException("RETURN_FLIGHT_STATUS_INVALID", "Unknown return-flight status");
        }
        String previous = incident.getReturnFlightStatus();
        Set<String> allowed = switch (previous) {
            case "RETURN_REQUESTED" -> Set.of("COMMAND_ACCEPTED", "FAILED", "MANUAL_INTERVENTION_REQUIRED");
            case "COMMAND_ACCEPTED" -> Set.of("RETURNING", "FAILED", "MANUAL_INTERVENTION_REQUIRED");
            case "RETURNING" -> Set.of("COMPLETED", "FAILED", "MANUAL_INTERVENTION_REQUIRED");
            default -> Set.of();
        };
        requireState(next, allowed, "RETURN_FLIGHT_TRANSITION_INVALID");
        incident.setReturnFlightStatus(next);
        if ("FAILED".equals(next) || "MANUAL_INTERVENTION_REQUIRED".equals(next)) {
            incident.setStatus("MANUAL_INTERVENTION_REQUIRED");
        }
        incidentRepository.save(incident);
        timeline(incident, "RETURN_FLIGHT_UPDATED", previous, next, null, request.detail(), null);
        return toResponse(incident);
    }

    @Transactional
    public DroneParcelIncidentResponse propose(
            Long incidentId, Long adminUserId, DroneIncidentProposalRequest request) {
        DroneParcelIncident incident = locked(incidentId);
        requireState(incident.getRecoveryStatus(), Set.of("VERIFIED", "CLOSED"), "RECOVERY_NOT_VERIFIED");
        String type = request.resolutionType().trim().toUpperCase(Locale.ROOT);
        if (!RESOLUTION_TYPES.contains(type)) {
            throw new BusinessException("RESOLUTION_TYPE_INVALID", "Unsupported resolution type");
        }
        LockerOrder order = orderRepository.findById(incident.getOrderId())
                .orElseThrow(() -> new NotFoundException("LockerOrder", incident.getOrderId()));
        boolean policyEnabled = order.getIncidentCompensationEnabled() != null
                ? order.getIncidentCompensationEnabled()
                : rules.incidentPolicy().enabled();
        if (!policyEnabled) {
            throw new BusinessException("INCIDENT_POLICY_DISABLED", "Incident compensation policy is disabled");
        }
        BigDecimal suggested = suggestedCompensation(order);
        BigDecimal amount = request.compensationAmount() == null ? BigDecimal.ZERO : request.compensationAmount();
        boolean override = amount.compareTo(suggested) > 0;
        if (override && !StringUtils.hasText(request.overrideReason())) {
            throw new BusinessException("COMPENSATION_OVERRIDE_REASON_REQUIRED", "Override reason is required");
        }
        boolean redelivery = Boolean.TRUE.equals(request.redeliveryOffered());
        boolean freeRedeliveryAllowed = order.getIncidentFreeRedelivery() != null
                ? order.getIncidentFreeRedelivery()
                : rules.incidentPolicy().freeRedelivery();
        if (redelivery && !freeRedeliveryAllowed) {
            throw new BusinessException("FREE_REDELIVERY_DISABLED", "Free redelivery is disabled by the order policy");
        }
        if (redelivery && !"RETURNED_TO_HUB".equals(incident.getParcelStatus())) {
            throw new BusinessException("PARCEL_NOT_AT_HUB",
                    "Redelivery can only be proposed after the parcel is confirmed at Hub");
        }
        if ("FREE_REDELIVERY".equals(type) && (!redelivery || amount.signum() != 0)
                || "REDELIVERY_PARTIAL_COMPENSATION".equals(type) && (!redelivery || amount.signum() <= 0)
                || "COMPENSATION_ONLY".equals(type) && (redelivery || amount.signum() <= 0)) {
            throw new BusinessException("RESOLUTION_CONTENT_INVALID",
                    "Resolution type does not match redelivery and compensation values");
        }
        // Thỏa thuận riêng: khách phải đọc được thoả thuận là gì trước khi bấm Đồng ý (test 2026-10-29 khách
        // chỉ thấy "Nhân viên liên hệ xử lý riêng").
        // Web admin bản trước gửi nội dung thoả thuận qua ô "Lý do điều chỉnh / Ghi chú thỏa thuận"
        // (`overrideReason`) ⇒ dùng ô đó khi không có `resolutionNote`.
        String resolutionNote = StringUtils.hasText(request.resolutionNote())
                ? request.resolutionNote()
                : "MANUAL_RESOLUTION".equals(type) ? request.overrideReason() : null;
        if ("MANUAL_RESOLUTION".equals(type) && !StringUtils.hasText(resolutionNote)) {
            throw new BusinessException("RESOLUTION_NOTE_REQUIRED",
                    "Thỏa thuận riêng cần ghi rõ nội dung thoả thuận gửi khách");
        }
        int version = proposalRepository.findFirstByIncidentIdOrderByProposalVersionDesc(incidentId)
                .map(value -> value.getProposalVersion() + 1).orElse(1);
        DroneIncidentResolutionProposal proposal = new DroneIncidentResolutionProposal();
        proposal.setIncidentId(incidentId);
        proposal.setProposalVersion(version);
        proposal.setResolutionType(type);
        proposal.setRedeliveryOffered(redelivery);
        proposal.setCompensationAmount(amount);
        proposal.setRefundShippingFee(Boolean.TRUE.equals(request.refundShippingFee()));
        proposal.setPolicyVersion(StringUtils.hasText(order.getIncidentPolicyVersion())
                ? order.getIncidentPolicyVersion() : "LEGACY");
        proposal.setOverrideReason(trim(request.overrideReason()));
        proposal.setResolutionNote(trim(resolutionNote));
        proposal.setProposedByUserId(adminUserId);
        proposal.setStatus("AWAITING_CUSTOMER_ACCEPTANCE");
        proposalRepository.save(proposal);
        incident.setStatus("AWAITING_CUSTOMER_RESPONSE");
        incident.setRedeliveryStatus(redelivery ? "AWAITING_CUSTOMER_ACCEPTANCE" : "NOT_REQUIRED");
        incident.setCompensationStatus(amount.signum() > 0 ? "AWAITING_CUSTOMER_ACCEPTANCE" : "NOT_REQUIRED");
        incidentRepository.save(incident);
        timeline(incident, "RESOLUTION_PROPOSED", null, incident.getStatus(), adminUserId,
                "Proposal version " + version, json(Map.of("suggestedCompensation", suggested)));
        notifyResolutionQuietly(order, incident);
        return toResponse(incident);
    }

    @Transactional
    public DroneParcelIncidentResponse customerRespond(
            Long incidentId, Long customerUserId, DroneIncidentCustomerResponseRequest request) {
        DroneParcelIncident incident = locked(incidentId);
        LockerOrder order = orderRepository.findById(incident.getOrderId())
                .orElseThrow(() -> new NotFoundException("LockerOrder", incident.getOrderId()));
        if (!customerUserId.equals(order.getUserId()) && !customerUserId.equals(order.getReceiverUserId())) {
            throw new BusinessException("INCIDENT_CUSTOMER_FORBIDDEN", "This incident does not belong to you", HttpStatus.FORBIDDEN);
        }
        DroneIncidentResolutionProposal proposal = proposalRepository
                .findFirstByIncidentIdOrderByProposalVersionDesc(incidentId)
                .orElseThrow(() -> new BusinessException("RESOLUTION_PROPOSAL_NOT_FOUND", "No proposal is awaiting response"));
        requireState(proposal.getStatus(), Set.of("AWAITING_CUSTOMER_ACCEPTANCE"), "PROPOSAL_ALREADY_RESPONDED");
        String decision = request.decision().trim().toUpperCase(Locale.ROOT);
        boolean disputeAllowed = order.getIncidentDisputeAllowed() != null
                ? order.getIncidentDisputeAllowed()
                : rules.incidentPolicy().disputeAllowed();
        if ("REQUEST_REVIEW".equals(decision)) {
            if (!disputeAllowed) {
                throw new BusinessException("INCIDENT_DISPUTE_DISABLED", "Request review is disabled by policy");
            }
            proposal.setStatus("DISPUTED");
            incident.setStatus("DISPUTED");
            incident.setCompensationStatus("DISPUTED");
        } else if ("ACCEPT".equals(decision)) {
            proposal.setStatus("ACCEPTED");
            incident.setStatus("RESOLUTION_IN_PROGRESS");
            if (proposal.isRedeliveryOffered()) {
                incident.setRedeliveryStatus("ACCEPTED");
                requestRedelivery(incident, order, proposal, customerUserId);
            } else {
                closeOrderWithoutRedelivery(incident, order, customerUserId);
            }
            if (proposal.getCompensationAmount() != null && proposal.getCompensationAmount().signum() > 0) {
                boolean approvalRequired = order.getIncidentApprovalRequired() != null
                        ? order.getIncidentApprovalRequired()
                        : rules.incidentPolicy().approvalRequired();
                if (approvalRequired) {
                    incident.setCompensationStatus("AWAITING_APPROVAL");
                } else {
                    requestCompensation(incident, order, proposal, customerUserId);
                }
            }
        } else {
            throw new BusinessException("CUSTOMER_DECISION_INVALID", "Decision must be ACCEPT or REQUEST_REVIEW");
        }
        proposal.setCustomerResponseNote(trim(request.note()));
        proposal.setRespondedAt(LocalDateTime.now());
        proposalRepository.save(proposal);
        incidentRepository.save(incident);
        timeline(incident, "CUSTOMER_" + decision, null, incident.getStatus(), customerUserId, request.note(), null);
        if ("ACCEPT".equals(decision)) {
            // Thoả thuận riêng không giao lại, không bồi thường qua hệ thống ⇒ không còn bước nào, đóng luôn.
            resolveIfSettled(incident, customerUserId);
        }
        return toResponse(incident);
    }

    @Transactional
    public DroneParcelIncidentResponse approveCompensation(Long incidentId, Long adminUserId, String roles) {
        requireAdmin(roles);
        DroneParcelIncident incident = locked(incidentId);
        DroneIncidentResolutionProposal proposal = proposalRepository
                .findFirstByIncidentIdOrderByProposalVersionDesc(incidentId)
                .orElseThrow(() -> new BusinessException("RESOLUTION_PROPOSAL_NOT_FOUND", "No proposal exists"));
        requireState(proposal.getStatus(), Set.of("ACCEPTED"), "COMPENSATION_NOT_ACCEPTED");
        if (proposal.getCompensationAmount() == null || proposal.getCompensationAmount().signum() <= 0) {
            throw new BusinessException("COMPENSATION_NOT_REQUIRED", "The accepted proposal has no compensation");
        }
        requireState(incident.getCompensationStatus(),
                Set.of("AWAITING_APPROVAL", "PAYMENT_PENDING", "PAYMENT_FAILED"),
                "COMPENSATION_NOT_APPROVABLE");
        LockerOrder order = orderRepository.findById(incident.getOrderId())
                .orElseThrow(() -> new NotFoundException("LockerOrder", incident.getOrderId()));
        requestCompensation(incident, order, proposal, adminUserId);
        incidentRepository.save(incident);
        return toResponse(incident);
    }

    @Transactional
    public DroneParcelIncidentResponse updateCompensation(
            String incidentCode, IncidentCompensationUpdateRequest request) {
        DroneParcelIncident incident = incidentRepository.findByIncidentCode(incidentCode)
                .orElseThrow(() -> new BusinessException("DRONE_INCIDENT_NOT_FOUND", "Incident not found"));
        String next = request.status().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PAID", "PAYMENT_FAILED").contains(next)) {
            throw new BusinessException("COMPENSATION_CALLBACK_INVALID", "Status must be PAID or PAYMENT_FAILED");
        }
        requireState(incident.getCompensationStatus(), Set.of("PROCESSING"),
                "COMPENSATION_CALLBACK_OUT_OF_ORDER");
        String previous = incident.getCompensationStatus();
        incident.setCompensationStatus(next);
        if (StringUtils.hasText(request.reference())) {
            incident.setCompensationReference(request.reference().trim());
        }
        incidentRepository.save(incident);
        timeline(incident, "COMPENSATION_" + next, previous, next, null, request.detail(),
                json(Map.of("reference", request.reference() == null ? "" : request.reference())));
        if ("PAID".equals(next)) {
            resolveIfSettled(incident, null);
        }
        return toResponse(incident);
    }

    private DroneParcelIncidentResponse toResponse(DroneParcelIncident incident) {
        LockerOrder order = orderRepository.findById(incident.getOrderId()).orElse(null);
        String inspectionStatus = synchronizedInspectionStatus(incident);
        List<DroneIncidentEvidenceResponse> evidence = evidenceRepository
                .findByIncidentIdOrderByCreatedAtAsc(incident.getId()).stream()
                .map(value -> new DroneIncidentEvidenceResponse(
                        value.getId(), value.getStage(), value.getSecureUrl(), value.getSecureUrl(), value.getCaption(),
                        value.getLatitude(), value.getLongitude(), value.getGpsAccuracyM(),
                        value.getCapturedAt(), value.getUploadedByUserId(), value.getCreatedAt()))
                .toList();
        List<DroneIncidentTimelineResponse> timeline = timelineRepository
                .findByIncidentIdOrderByCreatedAtAsc(incident.getId()).stream()
                .map(value -> new DroneIncidentTimelineResponse(
                        value.getId(), value.getEventType(), value.getFromStatus(), value.getToStatus(),
                        value.getActorUserId(), value.getNote(), value.getMetadataJson(), value.getCreatedAt()))
                .toList();
        List<DroneIncidentProposalResponse> proposals = proposalRepository
                .findByIncidentIdOrderByProposalVersionAsc(incident.getId()).stream()
                .map(value -> new DroneIncidentProposalResponse(
                        value.getId(), value.getProposalVersion(), value.getResolutionType(),
                        value.isRedeliveryOffered(), value.getCompensationAmount(), value.isRefundShippingFee(),
                        value.getPolicyVersion(), value.getOverrideReason(), value.getProposedByUserId(),
                        value.getStatus(), value.getCustomerResponseNote(), value.getRespondedAt(), value.getCreatedAt(),
                        value.getResolutionNote()))
                .toList();
        String recoveryTechName = null;
        String recoveryTechPhone = null;
        String recoveryTechRole = null;
        if (incident.getRecoveryAssignedToUserId() != null) {
            try {
                com.huynqb.laundrylocker.common.dto.UserSummary tech = userClient.getUser(incident.getRecoveryAssignedToUserId()).data();
                if (tech != null) {
                    recoveryTechName = tech.fullName();
                    recoveryTechPhone = tech.phoneNumber();
                    if (tech.roles() != null && tech.roles().contains("DRONE_TECHNICIAN")) {
                        recoveryTechRole = "DRONE_TECHNICIAN";
                    } else {
                        recoveryTechRole = "LOCKER_TECHNICIAN";
                    }
                }
            } catch (Exception ignored) {
            }
        }
        boolean policyEnabled = order != null && order.getIncidentCompensationEnabled() != null
                ? order.getIncidentCompensationEnabled()
                : rules.incidentPolicy().enabled();
        BigDecimal rate = order != null && order.getIncidentCompensationRate() != null
                ? order.getIncidentCompensationRate()
                : rules.incidentPolicy().compensationRate();
        BigDecimal cap = order != null && order.getIncidentCompensationCap() != null
                ? order.getIncidentCompensationCap()
                : rules.incidentPolicy().compensationCap();
        boolean freeRedelivery = order != null && order.getIncidentFreeRedelivery() != null
                ? order.getIncidentFreeRedelivery()
                : rules.incidentPolicy().freeRedelivery();
        boolean refundShippingFee = order != null && order.getIncidentRefundShippingFee() != null
                ? order.getIncidentRefundShippingFee()
                : rules.incidentPolicy().refundShippingFee();
        int recoverySlaHours = order != null && order.getIncidentRecoverySlaHours() != null
                ? order.getIncidentRecoverySlaHours()
                : rules.incidentPolicy().recoverySlaHours();
        boolean approvalRequired = order != null && order.getIncidentApprovalRequired() != null
                ? order.getIncidentApprovalRequired()
                : rules.incidentPolicy().approvalRequired();
        boolean disputeAllowed = order != null && order.getIncidentDisputeAllowed() != null
                ? order.getIncidentDisputeAllowed()
                : rules.incidentPolicy().disputeAllowed();
        BigDecimal suggested = policyEnabled ? suggestedCompensation(order, rate, cap) : null;
        BigDecimal declaredValue = order != null && order.getParcelDeclaredValue() != null
                ? order.getParcelDeclaredValue()
                : (order != null ? order.getTotalPrice() : null);
        return new DroneParcelIncidentResponse(
                incident.getId(), incident.getIncidentCode(), incident.getOrderId(),
                order == null ? null : order.getOrderCode(), incident.getMissionId(), incident.getDroneUnitId(),
                incident.getDroneCode(), order == null ? null : order.getUserId(), incident.getReportedByUserId(),
                incident.getReason(), incident.getStatus(), incident.getParcelStatus(), incident.getRecoveryStatus(),
                inspectionStatus, incident.getRedeliveryStatus(), incident.getCompensationStatus(),
                incident.getReturnFlightStatus(), incident.getDropLatitude(), incident.getDropLongitude(),
                incident.getGpsAccuracyM(), incident.getGpsSource(), incident.getTelemetryObservedAt(),
                incident.isTelemetryStale(), incident.getTelemetryJson(), incident.getCameraStatus(),
                incident.getCameraSnapshotUrl(), incident.getInspectionReportId(),
                incident.getRecoveryAssignedToUserId(), incident.getRecoveryLockerId(),
                incident.getRecoveryOutcome(), incident.getParcelCondition(), incident.getRecoveryNote(),
                incident.getRecoveredLatitude(), incident.getRecoveredLongitude(), incident.getRecoveredGpsAccuracyM(),
                incident.getReturnedToHubAt(), incident.getRedeliveryOrderId(), incident.getCompensationReference(),
                incident.getCreatedAt(), incident.getUpdatedAt(), evidence, timeline, proposals,
                suggested,
                disputeAllowed,
                order == null ? null : order.getStatus(),
                order == null ? null : order.getDeliveryStage(),
                rate,
                declaredValue,
                recoveryTechName,
                recoveryTechPhone,
                recoveryTechRole,
                cap,
                freeRedelivery,
                refundShippingFee,
                recoverySlaHours,
                approvalRequired,
                policyEnabled);
    }

    private void requestCompensation(DroneParcelIncident incident, LockerOrder order,
                                     DroneIncidentResolutionProposal proposal, Long actorUserId) {
        incident.setCompensationStatus("PAYMENT_PENDING");
        IncidentCompensationGateway.Submission result = compensationGateway.submit(
                incident.getId(), order.getId(), order.getUserId(), proposal.getCompensationAmount(),
                proposal.isRefundShippingFee(),
                "drone-incident-compensation-" + incident.getId() + "-v" + proposal.getProposalVersion());
        if (result.accepted()) {
            incident.setCompensationStatus("PROCESSING");
            incident.setCompensationReference(result.reference());
        }
        timeline(incident,
                result.integrationAvailable() ? "COMPENSATION_SUBMITTED" : "COMPENSATION_INTEGRATION_UNAVAILABLE",
                "PAYMENT_PENDING", incident.getCompensationStatus(), actorUserId, result.detail(),
                json(Map.of("integrationAvailable", result.integrationAvailable(), "accepted", result.accepted())));
    }

    private void requestRedelivery(DroneParcelIncident incident, LockerOrder order,
                                   DroneIncidentResolutionProposal proposal, Long actorUserId) {
        IncidentRedeliveryGateway.Submission result = redeliveryGateway.createAttempt(
                incident.getId(), order.getId(), order.getUserId(),
                "drone-incident-redelivery-" + incident.getId() + "-v" + proposal.getProposalVersion());
        if (result.accepted()) {
            incident.setRedeliveryStatus("PREPARING");
            incident.setRedeliveryOrderId(result.deliveryOrderId());
        }
        timeline(incident,
                result.integrationAvailable() ? "REDELIVERY_REQUESTED" : "REDELIVERY_INTEGRATION_UNAVAILABLE",
                "ACCEPTED", incident.getRedeliveryStatus(), actorUserId, result.detail(),
                json(Map.of("integrationAvailable", result.integrationAvailable(), "accepted", result.accepted())));
    }

    private String synchronizedInspectionStatus(DroneParcelIncident incident) {
        if (incident.getInspectionReportId() == null) {
            return incident.getInspectionStatus();
        }
        try {
            IncidentInspectionStatus report = lockerDroneClient
                    .getIncidentInspectionReport(incident.getInspectionReportId()).data();
            if (report == null || report.status() == null) {
                return incident.getInspectionStatus();
            }
            return switch (report.status().toUpperCase(Locale.ROOT)) {
                case "OPEN" -> report.assignedToUserId() == null ? "AWAITING_ASSIGNMENT" : "ASSIGNED";
                case "IN_PROGRESS" -> "INSPECTING";
                case "RESOLVED" -> "RESOLVED";
                default -> incident.getInspectionStatus();
            };
        } catch (RuntimeException ignored) {
            return incident.getInspectionStatus();
        }
    }

    private void saveEvidence(
            Long incidentId, String stage, VerifiedMedia media, String caption,
            Double latitude, Double longitude, Double accuracy, Instant capturedAt, Long actorUserId) {
        if (evidenceRepository.existsByPublicId(media.publicId())) {
            throw new BusinessException("INCIDENT_EVIDENCE_DUPLICATE", "Evidence image is already attached", HttpStatus.CONFLICT);
        }
        DroneIncidentEvidence evidence = new DroneIncidentEvidence();
        evidence.setIncidentId(incidentId);
        evidence.setStage(stage);
        evidence.setPublicId(media.publicId());
        evidence.setSecureUrl(media.secureUrl());
        evidence.setCaption(trim(caption));
        evidence.setLatitude(latitude);
        evidence.setLongitude(longitude);
        evidence.setGpsAccuracyM(accuracy);
        evidence.setCapturedAt(capturedAt);
        evidence.setUploadedByUserId(actorUserId);
        evidenceRepository.save(evidence);
    }

    private void timeline(
            DroneParcelIncident incident, String event, String from, String to,
            Long actorUserId, String note, String metadataJson) {
        DroneIncidentTimeline item = new DroneIncidentTimeline();
        item.setIncidentId(incident.getId());
        item.setEventType(event);
        item.setFromStatus(from);
        item.setToStatus(to);
        item.setActorUserId(actorUserId);
        item.setNote(trim(note));
        item.setMetadataJson(metadataJson);
        timelineRepository.save(item);
    }

    /// Khách chọn phương án không giao lại: đơn gốc kết thúc tại đây và ô nhận đã giữ được nhả — nếu không, ô bị
    /// giữ mãi cho một kiện sẽ không bao giờ tới. (Có giao lại thì đơn giao lại dùng tiếp ô này.)
    private void closeOrderWithoutRedelivery(DroneParcelIncident incident, LockerOrder order, Long actorUserId) {
        if (Set.of("CANCELED", "COMPLETED", "EXPIRED").contains(order.getStatus())) {
            return;
        }
        String previous = order.getStatus();
        order.setStatus("CANCELED");
        order.setStaffNote("Đóng đơn do sự cố rơi kiện (" + incident.getIncidentCode()
                + "); khách chọn phương án không giao lại");
        Long boxId = order.getReservedBoxId();
        orderRepository.save(order);
        if (boxId != null) {
            try {
                lockerClient.releaseBox(boxId);
            } catch (RuntimeException ex) {
                log.warn("Could not release box {} of order {} after drop incident: {}", boxId, order.getId(),
                        ex.getMessage());
            }
        }
        addOrderHistory(order.getId(), previous, "CANCELED", actorUserId,
                "Đóng đơn do sự cố rơi kiện " + incident.getIncidentCode() + " — không giao lại, đã nhả ô nhận");
    }

    private void addOrderHistory(Long orderId, String from, String to, Long actor, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(orderId);
        history.setOldStatus(from);
        history.setNewStatus(to);
        history.setChangedByUserId(actor);
        history.setNote(note);
        orderHistoryRepository.save(history);
    }

    private Location incidentLocation(
            ReportDroppedParcelRequest request,
            DroneTelemetryFrame telemetry,
            boolean telemetryLive,
            LockerOrder order,
            DroneMission mission) {
        if (telemetryLive && telemetry != null && telemetry.hasPosition()) {
            return new Location(telemetry.lat(), telemetry.lng(), null, "TELEMETRY");
        }
        if (request.latitude() != null && request.longitude() != null) {
            validateCoordinate(request.latitude(), request.longitude());
            return new Location(request.latitude(), request.longitude(), request.gpsAccuracyM(), "MANUAL_DEVICE");
        }
        double[] route = calculateRouteCoordinates(order, mission);
        if (route != null) {
            return new Location(route[0], route[1], request.gpsAccuracyM(), "FLIGHT_PATH");
        }
        return new Location(FPT_HCM_LATITUDE, FPT_HCM_LONGITUDE, null, "FALLBACK_DEFAULT");
    }

    private double[] calculateRouteCoordinates(LockerOrder order, DroneMission mission) {
        try {
            Long sourceId = mission != null && mission.getSourceLockerId() != null
                    ? mission.getSourceLockerId() : order.getSourceLockerId();
            Long destId = order.getDestinationLockerId() != null
                    ? order.getDestinationLockerId() : order.getLockerId();
            if (sourceId == null || destId == null) {
                return null;
            }
            if (lockerLookupClient == null) {
                return null;
            }
            var response = lockerLookupClient.getLockers(List.of(sourceId, destId));
            List<LockerInfo> lockers = response == null ? null : response.data();
            if (lockers == null || lockers.isEmpty()) {
                return null;
            }
            LockerInfo sourceLocker = null;
            LockerInfo destLocker = null;
            for (LockerInfo locker : lockers) {
                if (Objects.equals(locker.id(), sourceId)) {
                    sourceLocker = locker;
                }
                if (Objects.equals(locker.id(), destId)) {
                    destLocker = locker;
                }
            }
            if (sourceLocker == null || destLocker == null) {
                return null;
            }
            Double srcLat = sourceLocker.latitude();
            Double srcLng = sourceLocker.longitude();
            Double dstLat = destLocker.latitude();
            Double dstLng = destLocker.longitude();
            if (srcLat == null || srcLng == null || dstLat == null || dstLng == null) {
                return null;
            }
            String stage = mission != null ? mission.getStatus() : order.getDeliveryStage();
            double progress = 0.5;
            if ("LAUNCHING".equalsIgnoreCase(stage)) progress = 0.05;
            else if ("DEPARTED".equalsIgnoreCase(stage)) progress = 0.2;
            else if ("EN_ROUTE".equalsIgnoreCase(stage)) progress = 0.525;
            else if ("APPROACHING".equalsIgnoreCase(stage)) progress = 0.85;
            else if ("ARRIVED".equalsIgnoreCase(stage)) progress = 0.95;

            double lat = srcLat + (dstLat - srcLat) * progress;
            double lng = srcLng + (dstLng - srcLng) * progress;
            return new double[] { lat, lng };
        } catch (Exception ex) {
            return null;
        }
    }

    /// Đủ pin theo quy tắc `drone-return-min-battery-percent`; thấp hơn thì để autopilot tự hạ cánh an toàn.
    private boolean canRequestReturn(DroneTelemetryFrame telemetry, boolean telemetryLive) {
        return telemetryLive && telemetry != null && telemetry.autopilotConnected()
                && telemetry.airborne() && telemetry.batteryPercent() != null
                && telemetry.batteryPercent() >= rules.droneReturnMinBatteryPercent();
    }

    private static void validateCoordinate(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)
                || latitude != null && (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)) {
            throw new BusinessException("INCIDENT_GPS_INVALID", "Latitude and longitude are invalid");
        }
    }

    private void assertReporter(Long actorUserId, String roles, DroneMission mission) {
        boolean droneTechnician = UserRoles.parse(roles).contains("DRONE_TECHNICIAN");
        if (!UserRoles.isAdmin(roles)
                && (!droneTechnician || !actorUserId.equals(mission.getAssignedByUserId()))) {
            throw new BusinessException(
                    "DRONE_INCIDENT_REPORT_FORBIDDEN",
                    "Only the assigned drone technician or Admin may report this incident",
                    HttpStatus.FORBIDDEN);
        }
    }

    private static void requireAdmin(String roles) {
        if (!UserRoles.isAdmin(roles)) {
            throw new BusinessException("ADMIN_REQUIRED", "Admin role is required", HttpStatus.FORBIDDEN);
        }
    }

    private static void requireDroneTechnician(String roles) {
        if (!UserRoles.parse(roles).contains("DRONE_TECHNICIAN")) {
            throw new BusinessException(
                    "DRONE_TECHNICIAN_REQUIRED", "Chỉ KTV drone dùng được chức năng này", HttpStatus.FORBIDDEN);
        }
    }

    private void notifyIncidentCreatedQuietly(
            DroneParcelIncident incident, LockerOrder order, IncidentTicketBundle tickets) {
        notifyQuietly(order.getUserId(), "Sự cố giao hàng bằng drone",
                "Kiện hàng của đơn " + order.getOrderCode() + " được báo rơi. Hệ thống đang điều phối thu hồi.",
                "DRONE_PARCEL_DROP_REPORTED", order.getId());
        if (order.getReceiverUserId() != null && !order.getReceiverUserId().equals(order.getUserId())) {
            notifyQuietly(order.getReceiverUserId(), "Sự cố giao hàng bằng drone",
                    "Đơn " + order.getOrderCode() + " đang được xử lý sau sự cố rơi kiện.",
                    "DRONE_PARCEL_DROP_REPORTED", order.getId());
        }
        if (tickets != null && tickets.recoveryTechnicianId() != null) {
            notifyQuietly(tickets.recoveryTechnicianId(), "Nhiệm vụ thu hồi kiện drone",
                    "Bạn được phân công thu hồi kiện cho sự cố " + incident.getIncidentCode() + ".",
                    "DRONE_PARCEL_RECOVERY_ASSIGNED", incident.getId());
        }
    }

    private void notifyResolutionQuietly(LockerOrder order, DroneParcelIncident incident) {
        notifyQuietly(order.getUserId(), "Phương án xử lý sự cố drone",
                "Đơn " + order.getOrderCode() + " đã có phương án xử lý cần bạn phản hồi.",
                "DRONE_INCIDENT_RESOLUTION_PROPOSED", order.getId());
        if (order.getReceiverUserId() != null && !order.getReceiverUserId().equals(order.getUserId())) {
            notifyQuietly(order.getReceiverUserId(), "Phương án xử lý sự cố drone",
                    "Đơn " + order.getOrderCode() + " đã có phương án xử lý.",
                    "DRONE_INCIDENT_RESOLUTION_PROPOSED", order.getId());
        }
    }

    private void notifyQuietly(Long userId, String title, String message, String type, Long incidentId) {
        if (userId == null) {
            return;
        }
        try {
            notificationClient.requestNotification(
                    new NotificationRequest(userId, title, message, type, incidentId, "DRONE_INCIDENT"));
        } catch (RuntimeException ignored) {
            // Incident state is authoritative; notification delivery is best effort.
        }
    }

    private void assertCanView(DroneParcelIncident incident, Long actorUserId, String roles) {
        if (UserRoles.isAdmin(roles)
                || actorUserId.equals(incident.getReportedByUserId())
                || actorUserId.equals(incident.getRecoveryAssignedToUserId())) {
            return;
        }
        LockerOrder order = orderRepository.findById(incident.getOrderId()).orElse(null);
        if (order != null && (actorUserId.equals(order.getUserId()) || actorUserId.equals(order.getReceiverUserId()))) {
            return;
        }
        throw new BusinessException("INCIDENT_FORBIDDEN", "You cannot view this incident", HttpStatus.FORBIDDEN);
    }

    private static void assertRecoveryAssignee(DroneParcelIncident incident, Long actorUserId) {
        if (!actorUserId.equals(incident.getRecoveryAssignedToUserId())) {
            throw new BusinessException("RECOVERY_FORBIDDEN", "This recovery ticket is assigned to another technician", HttpStatus.FORBIDDEN);
        }
    }

    private static void requireState(String actual, Set<String> allowed, String code) {
        if (!allowed.contains(actual)) {
            throw new BusinessException(code, "Invalid state transition from " + actual, HttpStatus.CONFLICT);
        }
    }

    private DroneParcelIncident find(Long id) {
        return incidentRepository.findById(id).orElseThrow(() -> new NotFoundException("DroneParcelIncident", id));
    }

    private DroneParcelIncident locked(Long id) {
        return incidentRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("DroneParcelIncident", id));
    }

    private static void requireIdempotencyKey(String key) {
        if (!StringUtils.hasText(key) || key.trim().length() > 120) {
            throw new BusinessException("IDEMPOTENCY_KEY_INVALID", "A valid Idempotency-Key is required");
        }
    }

    private static String normalizeCameraStatus(String status, VerifiedMedia snapshot) {
        if (snapshot != null) {
            return "SNAPSHOT_CAPTURED";
        }
        if (!StringUtils.hasText(status)) {
            return "UNAVAILABLE";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return Set.of("CONNECTING", "LIVE", "RECONNECTING", "OFFLINE", "UNAVAILABLE", "ERROR")
                .contains(normalized) ? normalized : "UNAVAILABLE";
    }

    private BigDecimal suggestedCompensation(LockerOrder order, BigDecimal rate, BigDecimal cap) {
        BigDecimal base = order == null ? null : order.getParcelDeclaredValue();
        if ((base == null || base.signum() <= 0) && order != null && order.getTotalPrice() != null) {
            base = order.getTotalPrice();
        }
        if (base == null) {
            base = BigDecimal.ZERO;
        }
        BigDecimal resolvedRate = rate != null ? rate
                : (order != null && order.getIncidentCompensationRate() != null
                ? order.getIncidentCompensationRate() : rules.incidentPolicy().compensationRate());
        if (resolvedRate == null) {
            resolvedRate = BigDecimal.valueOf(60);
        }
        BigDecimal result = base.multiply(resolvedRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal resolvedCap = cap != null ? cap
                : (order != null ? order.getIncidentCompensationCap() : rules.incidentPolicy().compensationCap());
        return resolvedCap != null && resolvedCap.signum() > 0 ? result.min(resolvedCap) : result;
    }

    private BigDecimal suggestedCompensation(LockerOrder order) {
        return suggestedCompensation(order, null, null);
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new BusinessException("INCIDENT_METADATA_INVALID", "Could not capture incident metadata");
        }
    }

    private static Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (DateTimeParseException error) {
            throw new BusinessException("EVIDENCE_CAPTURED_AT_INVALID", "capturedAt must be ISO-8601 with timezone");
        }
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record Location(Double latitude, Double longitude, Double accuracyM, String source) {
    }
}
