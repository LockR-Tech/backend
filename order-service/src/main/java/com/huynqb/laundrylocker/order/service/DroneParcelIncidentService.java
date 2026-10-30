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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
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

    /// Thư cho người gửi / người nhận khi có sự cố sau lúc đã báo "đơn đang trên đường" (không bắt buộc trong test).
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DroneDeliveryMailer mailer;

    /// Chọn trạm bay nhận kiện rơi đã tìm thấy (không có trong unit test cũ ⇒ bỏ qua bước điều phối).
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DroneSourceLockerSelector stationSelector;

    /// Ghi nhật ký thư sau commit cần giao dịch riêng (không có trong unit test ⇒ ghi thẳng).
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PlatformTransactionManager transactionManager;

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
                "Báo sự cố rơi kiện " + incident.getIncidentCode());

        try {
            lockerDroneClient.transitionDroneStatus(
                    mission.getDroneUnitId(),
                    new DroneStatusTransitionRequest(
                            "IN_FLIGHT", "FAULT", "Parcel drop incident " + incident.getIncidentCode(), false));
        } catch (Exception ignored) {
            // Fleet state may already be FAULT/offline. The incident remains the
            // source of truth and the drone is never set back to IDLE here.
        }

        // locker-service lỗi không được chặn việc báo rơi: drone đã mất kiện, sự cố và việc dừng nhiệm vụ phải ghi
        // được. Không có phiếu ⇒ thu hồi chờ admin phân công tay (AWAITING_ASSIGNMENT).
        IncidentTicketBundle tickets = null;
        try {
            var ticketResponse = lockerDroneClient.createIncidentTickets(new CreateIncidentTicketsCommand(
                    incident.getId(), incident.getIncidentCode(), order.getId(), order.getOrderCode(),
                    mission.getDroneUnitId(), mission.getDroneCode(), actorUserId, request.reason(),
                    location.latitude(), location.longitude(), location.accuracyM(), incident.getCameraSnapshotUrl()));
            tickets = ticketResponse == null ? null : ticketResponse.data();
        } catch (RuntimeException ex) {
            log.warn("Incident {} reported without locker tickets: {}", incident.getIncidentCode(), ex.getMessage());
        }
        if (tickets == null) {
            incident.setRecoveryStatus("AWAITING_ASSIGNMENT");
            incident.setParcelStatus("RECOVERY_PENDING");
        } else {
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
        // Drone không nhận lệnh quay về chỉ là việc của bước "drone quay về" (đội bay đưa về rồi admin xác nhận tay);
        // sự cố vẫn đi tiếp luồng thu hồi. Trước đây cả sự cố mang nhãn "Cần can thiệp thủ công" ngay khi báo — ở
        // DEMO (không telemetry) là mọi sự cố.
        incident.setStatus("INVESTIGATING");
        incident = incidentRepository.save(incident);
        timeline(incident, "INCIDENT_REPORTED", null, incident.getStatus(), actorUserId, request.reason(), null);
        timeline(incident, "RETURN_REQUEST", null, incident.getReturnFlightStatus(), actorUserId, rtl.detail(), null);
        if (tickets == null) {
            timeline(incident, "RECOVERY_TICKETS_UNAVAILABLE", null, incident.getRecoveryStatus(), null,
                    "Chưa tạo được phiếu kiểm tra/thu hồi bên tủ — admin phân công KTV thu hồi bằng tay", null);
        }
        DroneParcelIncident created = incident;
        IncidentTicketBundle createdTickets = tickets;
        afterCommit(() -> notifyIncidentCreatedQuietly(created, order, createdTickets));
        mailDropReported(order, mission, incident);
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
            incidents = incidentRepository.findVisibleToCustomer(actorUserId);
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
        String code = incident.getIncidentCode();
        Long submittedId = incident.getId();
        String result = "FOUND".equals(outcome) ? "đã tìm thấy kiện"
                : "NOT_FOUND".equals(outcome) ? "không tìm thấy kiện" : "không tiếp cận được khu vực";
        afterCommit(() -> notifyAdminsQuietly("Kết quả thu hồi chờ xác minh",
                "KTV báo " + result + " cho sự cố " + code + ". Vui lòng xác minh kết quả.",
                "DRONE_INCIDENT_RECOVERY_SUBMITTED", submittedId));
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
        if ("FOUND".equals(incident.getRecoveryOutcome())) {
            dispatchToRecoveryStation(incident, adminUserId);
        }
        announceRecoveryVerified(incident);
        return toResponse(incident);
    }

    /// Kiện đã tìm thấy và admin đã xác minh ⇒ điều phối về trạm bay gần nơi tìm thấy nhất còn drone, để đơn giao
    /// lại (khi khách đồng ý) bay thẳng từ trạm đó. KTV thu hồi thấy tên, địa chỉ, toạ độ trạm để chỉ đường.
    /// Hết drone ⇒ trạm gần nhất có bãi đáp sẵn sàng và báo admin; không có trạm ⇒ giữ tủ phụ trách cũ, báo admin.
    private void dispatchToRecoveryStation(DroneParcelIncident incident, Long adminUserId) {
        if (stationSelector == null) {
            return;
        }
        LockerOrder order = orderRepository.findById(incident.getOrderId()).orElse(null);
        Double latitude = incident.getRecoveredLatitude() != null ? incident.getRecoveredLatitude()
                : incident.getDropLatitude();
        Double longitude = incident.getRecoveredLongitude() != null ? incident.getRecoveredLongitude()
                : incident.getDropLongitude();
        Long destinationId = order == null ? null
                : order.getDestinationLockerId() != null ? order.getDestinationLockerId() : order.getLockerId();
        var station = latitude == null || longitude == null || destinationId == null
                ? java.util.Optional.<DroneSourceLockerSelector.RecoveryStation>empty()
                : stationSelector.recoveryStation(latitude, longitude, destinationId);
        String code = incident.getIncidentCode();
        Long incidentId = incident.getId();
        if (station.isEmpty()) {
            timeline(incident, "RECOVERY_STATION_UNAVAILABLE", null, null, adminUserId,
                    "Không có trạm bay nào nhận được kiện (bãi đáp sẵn sàng, trong tầm bay tới tủ nhận) — "
                            + "giữ nơi bàn giao cũ, admin điều phối tay", null);
            afterCommit(() -> notifyAdminsQuietly("Chưa điều phối được trạm cho kiện tìm thấy",
                    "Sự cố " + code + ": không có trạm bay nào nhận được kiện, cần điều phối tay.",
                    "DRONE_INCIDENT_STATION_UNAVAILABLE", incidentId));
            return;
        }
        DroneSourceLockerSelector.RecoveryStation selected = station.get();
        LockerInfo locker = selected.locker();
        Long previousLocker = incident.getRecoveryLockerId();
        incident.setRecoveryLockerId(locker.id());
        incident.setRecoveryStationDispatchedAt(LocalDateTime.now());
        incident.setRecoveryStationName(StringUtils.hasText(locker.name()) ? locker.name() : locker.code());
        incident.setRecoveryStationAddress(locker.address());
        incident.setRecoveryStationLatitude(locker.latitude());
        incident.setRecoveryStationLongitude(locker.longitude());
        incident.setRecoveryStationDistanceM(selected.distanceMeters());
        incident.setRecoveryStationDroneAvailable(selected.droneAvailable());
        incidentRepository.save(incident);
        String stationName = incident.getRecoveryStationName();
        String distance = String.format(Locale.ROOT, "%.1f km", selected.distanceMeters() / 1000.0);
        timeline(incident, "RECOVERY_STATION_DISPATCHED", null, null, adminUserId,
                "Điều phối kiện về trạm " + stationName + " (cách nơi tìm thấy ~" + distance + ")"
                        + (selected.droneAvailable() ? "" : " — trạm đang hết drone, kiện chờ drone tại trạm"),
                json(Map.of("previousLockerId", previousLocker == null ? "" : previousLocker,
                        "stationLockerId", locker.id(), "distanceMeters", selected.distanceMeters(),
                        "droneAvailable", selected.droneAvailable())));
        Long technicianId = incident.getRecoveryAssignedToUserId();
        String address = StringUtils.hasText(locker.address()) ? " — " + locker.address() : "";
        boolean droneAvailable = selected.droneAvailable();
        afterCommit(() -> {
            notifyStaffQuietly(technicianId, "Mang kiện tới trạm " + stationName,
                    "Sự cố " + code + ": mang kiện tới trạm " + stationName + address + " (~" + distance
                            + "), rồi xác nhận bàn giao tại trạm.",
                    "DRONE_PARCEL_RECOVERY_STATION", incidentId);
            if (!droneAvailable) {
                notifyAdminsQuietly("Trạm nhận kiện đang hết drone",
                        "Sự cố " + code + ": kiện được điều phối về trạm " + stationName
                                + " nhưng trạm chưa có drone sẵn sàng để giao lại.",
                        "DRONE_INCIDENT_STATION_NO_DRONE", incidentId);
            }
        });
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
        Long technicianId = request.technicianId();
        String code = incident.getIncidentCode();
        Long assignedId = incident.getId();
        afterCommit(() -> notifyStaffQuietly(technicianId, "Nhiệm vụ thu hồi kiện drone",
                "Bạn được phân công thu hồi kiện cho sự cố " + code + ".",
                "DRONE_PARCEL_RECOVERY_ASSIGNED", assignedId));
        return toResponse(incident);
    }

    @Transactional
    public DroneParcelIncidentResponse completeRedelivery(Long incidentId, Long adminUserId, String roles) {
        requireAdmin(roles);
        DroneParcelIncident incident = locked(incidentId);
        requireState(incident.getRedeliveryStatus(), Set.of("PREPARING", "IN_PROGRESS"), "REDELIVERY_NOT_IN_PROGRESS");
        // Đơn giao lại hoàn tất thì sự cố tự đóng phần giao lại (onRedeliveryOrderCompleted). Bấm tay chỉ còn để gỡ
        // sự cố cũ (đơn giao lại đã xong trước bản tự đóng) — không cho đóng khi kiện chưa tới tay khách
        // (test 2026-10-29: nút hiện ngay lúc đơn giao lại còn chờ điều phối).
        if (incident.getRedeliveryOrderId() != null) {
            String redeliveryStatus = orderRepository.findById(incident.getRedeliveryOrderId())
                    .map(LockerOrder::getStatus).orElse(null);
            if (!"COMPLETED".equals(redeliveryStatus)) {
                throw new BusinessException("REDELIVERY_ORDER_NOT_COMPLETED",
                        "Đơn giao lại chưa hoàn tất — sự cố tự đóng khi người nhận lấy hàng", HttpStatus.CONFLICT);
            }
        }
        markRedeliveryCompleted(incident, adminUserId, "Admin xác nhận đã giao lại hàng thành công cho khách hàng");
        return toResponse(incident);
    }

    /// Drone không nhận lệnh quay về từ xa (telemetry cũ / DEMO): đội bay đưa drone về trạm rồi admin xác nhận.
    /// Trước đây `MANUAL_INTERVENTION_REQUIRED` là trạng thái cụt — không có đường nào đóng bước này.
    @Transactional
    public DroneParcelIncidentResponse confirmDroneReturned(Long incidentId, Long adminUserId, String note) {
        DroneParcelIncident incident = locked(incidentId);
        String previous = incident.getReturnFlightStatus();
        requireState(previous, Set.of("RETURN_REQUESTED", "COMMAND_ACCEPTED", "RETURNING", "FAILED",
                "MANUAL_INTERVENTION_REQUIRED"), "RETURN_FLIGHT_ALREADY_CLOSED");
        incident.setReturnFlightStatus("COMPLETED");
        if ("MANUAL_INTERVENTION_REQUIRED".equals(incident.getStatus())) {
            incident.setStatus(incident.getRecoveryStatus() != null
                    && Set.of("SUBMITTED", "VERIFIED", "CLOSED").contains(incident.getRecoveryStatus())
                    ? "AWAITING_ADMIN_REVIEW" : "INVESTIGATING");
        }
        incidentRepository.save(incident);
        timeline(incident, "RETURN_FLIGHT_CONFIRMED", previous, "COMPLETED", adminUserId,
                StringUtils.hasText(note) ? note.trim() : "Admin xác nhận drone đã về trạm", null);
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
            // Không điều khiển được từ xa: đội bay đưa drone về rồi xác nhận tay.
            case "MANUAL_INTERVENTION_REQUIRED", "FAILED" -> Set.of("COMPLETED");
            default -> Set.of();
        };
        requireState(next, allowed, "RETURN_FLIGHT_TRANSITION_INVALID");
        incident.setReturnFlightStatus(next);
        // Chuyến bay về chỉ chặn sự cố khi còn ở bước điều tra ban đầu. Sự cố đã sang thu hồi / phương án / xử lý
        // thì giữ nguyên — bước quay về có cột riêng (returnFlightStatus), ghi đè làm mất trạng thái thật.
        if (("FAILED".equals(next) || "MANUAL_INTERVENTION_REQUIRED".equals(next))
                && Set.of("REPORTED", "INVESTIGATING").contains(incident.getStatus())) {
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
        // Phương án đang chờ khách, đã được nhận hoặc sự cố đã xong ⇒ không gửi thêm phiên bản (trước đây chỉ web ẩn
        // form; gọi thẳng API vẫn mở lại sự cố đã RESOLVED và chi bồi thường lần hai).
        if (Set.of("AWAITING_CUSTOMER_RESPONSE", "RESOLUTION_IN_PROGRESS", "RESOLVED", "CLOSED")
                .contains(incident.getStatus())) {
            throw new BusinessException("INCIDENT_NOT_PROPOSABLE",
                    "Sự cố đang chờ khách phản hồi hoặc đã có phương án được chấp nhận", HttpStatus.CONFLICT);
        }
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
        DroneIncidentResolutionProposal previous = proposalRepository
                .findFirstByIncidentIdOrderByProposalVersionDesc(incidentId).orElse(null);
        if (previous != null && "ACCEPTED".equals(previous.getStatus())) {
            throw new BusinessException("INCIDENT_NOT_PROPOSABLE",
                    "Khách đã đồng ý phương án lần " + previous.getProposalVersion(), HttpStatus.CONFLICT);
        }
        if (previous != null && Set.of("AWAITING_CUSTOMER_ACCEPTANCE", "DISPUTED").contains(previous.getStatus())) {
            // Phương án bị khách yêu cầu xem xét được thay bằng bản mới — khách chỉ phản hồi bản mới nhất.
            previous.setStatus("SUPERSEDED");
            proposalRepository.save(previous);
        }
        int version = previous == null ? 1 : previous.getProposalVersion() + 1;
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
                "Gửi phương án lần " + version, json(Map.of("suggestedCompensation", suggested)));
        afterCommit(() -> notifyResolutionQuietly(order, incident));
        mailProposal(order, incident, proposal);
        return toResponse(incident);
    }

    @Transactional
    public DroneParcelIncidentResponse customerRespond(
            Long incidentId, Long customerUserId, DroneIncidentCustomerResponseRequest request) {
        DroneParcelIncident incident = locked(incidentId);
        LockerOrder order = orderRepository.findById(incident.getOrderId())
                .orElseThrow(() -> new NotFoundException("LockerOrder", incident.getOrderId()));
        // Chỉ người gửi (chủ đơn) quyết định phương án: tiền bồi thường chuyển về tài khoản người gửi. Người nhận vẫn
        // xem được sự cố nhưng không đồng ý / yêu cầu xem xét thay.
        if (!customerUserId.equals(order.getUserId())) {
            throw new BusinessException("INCIDENT_CUSTOMER_FORBIDDEN",
                    customerUserId.equals(order.getReceiverUserId())
                            ? "Chỉ người gửi đơn được phản hồi phương án xử lý"
                            : "This incident does not belong to you",
                    HttpStatus.FORBIDDEN);
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
            // Chỉ khoản bồi thường mới bị "tranh chấp"; phương án không có tiền thì giữ NOT_REQUIRED.
            if (proposal.getCompensationAmount() != null && proposal.getCompensationAmount().signum() > 0) {
                incident.setCompensationStatus("DISPUTED");
            }
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
            mailResolutionAccepted(order, incident, proposal);
        }
        boolean accepted = "ACCEPT".equals(decision);
        String code = incident.getIncidentCode();
        Long respondedId = incident.getId();
        afterCommit(() -> notifyAdminsQuietly(
                accepted ? "Khách đồng ý phương án sự cố" : "Khách yêu cầu xem xét lại phương án",
                "Đơn " + order.getOrderCode() + " (" + code + "): "
                        + (accepted ? "khách đã đồng ý phương án." : "khách yêu cầu xem xét lại, cần gửi phương án mới."),
                accepted ? "DRONE_INCIDENT_CUSTOMER_ACCEPTED" : "DRONE_INCIDENT_CUSTOMER_DISPUTED", respondedId));
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
                policyEnabled,
                incident.getRecoveryStationName(),
                incident.getRecoveryStationAddress(),
                incident.getRecoveryStationLatitude(),
                incident.getRecoveryStationLongitude(),
                incident.getRecoveryStationDistanceM(),
                incident.getRecoveryStationDroneAvailable(),
                incident.getRecoveryStationDispatchedAt());
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
            // Sau commit: giao dịch hỏng thì đơn chưa đóng, ô không được nhả cho đơn khác.
            Long closedOrderId = order.getId();
            afterCommit(() -> {
                try {
                    lockerClient.releaseBox(boxId);
                } catch (RuntimeException ex) {
                    log.warn("Could not release box {} of order {} after drop incident: {}", boxId, closedOrderId,
                            ex.getMessage());
                }
            });
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
        notifyCustomerQuietly(order.getUserId(), "Sự cố giao hàng bằng drone",
                "Kiện hàng của đơn " + order.getOrderCode() + " được báo rơi. Hệ thống đang điều phối thu hồi.",
                "DRONE_PARCEL_DROP_REPORTED", order.getId());
        if (order.getReceiverUserId() != null && !order.getReceiverUserId().equals(order.getUserId())) {
            notifyCustomerQuietly(order.getReceiverUserId(), "Sự cố giao hàng bằng drone",
                    "Đơn " + order.getOrderCode() + " đang được xử lý sau sự cố rơi kiện.",
                    "DRONE_PARCEL_DROP_REPORTED", order.getId());
        }
        if (tickets != null && tickets.recoveryTechnicianId() != null) {
            notifyStaffQuietly(tickets.recoveryTechnicianId(), "Nhiệm vụ thu hồi kiện drone",
                    "Bạn được phân công thu hồi kiện cho sự cố " + incident.getIncidentCode() + ".",
                    "DRONE_PARCEL_RECOVERY_ASSIGNED", incident.getId());
        }
        notifyAdminsQuietly("Sự cố rơi kiện drone",
                "Đơn " + order.getOrderCode() + " báo rơi kiện (" + incident.getIncidentCode() + ")"
                        + (tickets == null || tickets.recoveryTechnicianId() == null
                                ? " — chưa có KTV thu hồi, cần phân công." : "."),
                "DRONE_PARCEL_DROP_REPORTED", incident.getId());
    }

    private void notifyResolutionQuietly(LockerOrder order, DroneParcelIncident incident) {
        notifyCustomerQuietly(order.getUserId(), "Phương án xử lý sự cố drone",
                "Đơn " + order.getOrderCode() + " đã có phương án xử lý cần bạn phản hồi.",
                "DRONE_INCIDENT_RESOLUTION_PROPOSED", order.getId());
        if (order.getReceiverUserId() != null && !order.getReceiverUserId().equals(order.getUserId())) {
            notifyCustomerQuietly(order.getReceiverUserId(), "Phương án xử lý sự cố drone",
                    "Đơn " + order.getOrderCode() + " đã có phương án xử lý.",
                    "DRONE_INCIDENT_RESOLUTION_PROPOSED", order.getId());
        }
    }

    /// Báo rơi kiện: người gửi luôn được báo; người nhận chỉ khi drone đã phóng (đã nhận thư "đang trên đường").
    /// Gửi sau commit: giao dịch hỏng thì không có thư cho một sự cố không tồn tại, và không giữ khoá đơn khi chờ SMTP.
    private void mailDropReported(LockerOrder order, DroneMission mission, DroneParcelIncident incident) {
        if (mailer == null) {
            return;
        }
        boolean launched = mission != null && mission.getLaunchingAt() != null;
        String location = incident.getDropLatitude() == null || incident.getDropLongitude() == null
                ? null
                : String.format(Locale.ROOT, "%.5f, %.5f", incident.getDropLatitude(), incident.getDropLongitude());
        String stage = DroneMissionProgressService.stageLabel(mission == null ? null : mission.getFailedStage());
        String code = incident.getIncidentCode();
        Instant reportedAt = Instant.now();
        afterCommit(() -> recordMail(order,
                mailer.parcelDropReported(order, code, reportedAt, location, stage, launched),
                "báo sự cố rơi kiện"));
    }

    /// Mail 2: admin đã xác minh kết quả thu hồi (tìm thấy / chưa tìm thấy) — kèm thông báo trong app.
    private void announceRecoveryVerified(DroneParcelIncident incident) {
        LockerOrder order = orderRepository.findById(incident.getOrderId()).orElse(null);
        if (order == null) {
            return;
        }
        boolean found = "FOUND".equals(incident.getRecoveryOutcome());
        String parcel = PaymentIncidentCompensationGateway.parcelLabel(incident);
        String parcelState = parcel == null ? null : Character.toUpperCase(parcel.charAt(0)) + parcel.substring(1);
        boolean launched = incident.getMissionId() != null && missionRepository.findById(incident.getMissionId())
                .map(DroneMission::getLaunchingAt).isPresent();
        String code = incident.getIncidentCode();
        String stationName = incident.getRecoveryStationName();
        Instant verifiedAt = Instant.now();
        String title = found ? "Đã tìm thấy kiện hàng" : "Chưa tìm thấy kiện hàng";
        String message = found
                ? "Kiện của đơn " + order.getOrderCode() + " đã được tìm thấy"
                        + (parcel == null ? "" : " (" + parcel + ")")
                        + (stationName == null ? "" : " và được đưa về trạm " + stationName)
                        + ". Lock.R sẽ gửi phương án xử lý."
                : "Kiện của đơn " + order.getOrderCode() + " chưa tìm thấy. Lock.R sẽ gửi phương án bồi thường.";
        afterCommit(() -> {
            notifyCustomerQuietly(order.getUserId(), title, message, "DRONE_PARCEL_RECOVERY_VERIFIED", order.getId());
            if (order.getReceiverUserId() != null && !order.getReceiverUserId().equals(order.getUserId())) {
                notifyCustomerQuietly(order.getReceiverUserId(), title, message, "DRONE_PARCEL_RECOVERY_VERIFIED",
                        order.getId());
            }
            if (mailer != null) {
                recordMail(order, mailer.parcelRecoveryVerified(order, code, found, parcelState, stationName, verifiedAt,
                        launched),
                        found ? "báo đã tìm thấy kiện" : "báo chưa tìm thấy kiện");
            }
        });
    }

    private void mailProposal(LockerOrder order, DroneParcelIncident incident,
            DroneIncidentResolutionProposal proposal) {
        if (mailer == null) {
            return;
        }
        String parcel = PaymentIncidentCompensationGateway.parcelLabel(incident);
        BigDecimal amount = proposal.getCompensationAmount();
        String compensation = amount == null || amount.signum() <= 0
                ? null
                : PaymentIncidentCompensationGateway.formatVnd(amount)
                        + (proposal.isRefundShippingFee() ? " (kèm hoàn phí giao)" : "");
        String code = incident.getIncidentCode();
        String resolution = PaymentIncidentCompensationGateway.resolutionLabel(proposal.getResolutionType());
        String parcelState = parcel == null ? null : Character.toUpperCase(parcel.charAt(0)) + parcel.substring(1);
        String note = proposal.getResolutionNote();
        afterCommit(() -> recordMail(order,
                mailer.incidentProposal(order, code, resolution, parcelState, compensation, note),
                "gửi phương án xử lý sự cố"));
    }

    private void mailResolutionAccepted(LockerOrder order, DroneParcelIncident incident,
            DroneIncidentResolutionProposal proposal) {
        if (mailer == null) {
            return;
        }
        String redeliveryCode = incident.getRedeliveryOrderId() == null ? null
                : orderRepository.findById(incident.getRedeliveryOrderId()).map(LockerOrder::getOrderCode).orElse(null);
        boolean launched = incident.getMissionId() != null && missionRepository.findById(incident.getMissionId())
                .map(DroneMission::getLaunchingAt).isPresent();
        String code = incident.getIncidentCode();
        String resolution = PaymentIncidentCompensationGateway.resolutionLabel(proposal.getResolutionType());
        afterCommit(() -> recordMail(order,
                mailer.incidentResolutionAccepted(order, code, resolution, redeliveryCode, launched),
                "xác nhận phương án xử lý sự cố"));
    }

    /// Nhật ký hành trình ghi thư đã tới ai — người gửi và nhân viên cùng xem được. Chạy sau commit nên cần giao
    /// dịch riêng, nếu không bản ghi tham gia vào giao dịch đã đóng và bị bỏ.
    private void recordMail(LockerOrder order, DroneDeliveryMailer.Outcome outcome, String purpose) {
        String reached = outcome == null ? null : outcome.describe(order);
        String note = reached == null
                ? "Chưa gửi được email " + purpose + "."
                : "Đã gửi email " + purpose + " cho " + reached + ".";
        try {
            inNewTransaction(() -> addOrderHistory(
                    order.getId(), order.getDeliveryStage(), order.getDeliveryStage(), null, note));
        } catch (RuntimeException ex) {
            log.warn("Could not record mail note for order {}: {}", order.getId(), ex.getMessage());
        }
    }

    /// Thông báo cho khách: tham chiếu là ĐƠN (app khách mở màn theo dõi đơn và tải lại theo orderId).
    private void notifyCustomerQuietly(Long userId, String title, String message, String type, Long orderId) {
        notifyQuietly(userId, title, message, type, orderId, "DRONE_ORDER");
    }

    /// Thông báo cho KTV/admin: tham chiếu là SỰ CỐ (mở thẳng chi tiết sự cố).
    private void notifyStaffQuietly(Long userId, String title, String message, String type, Long incidentId) {
        notifyQuietly(userId, title, message, type, incidentId, "DRONE_INCIDENT");
    }

    /// Việc chờ admin (phân công, xác minh kết quả, khách phản hồi): báo trong app cho mọi admin đang hoạt động.
    private void notifyAdminsQuietly(String title, String message, String type, Long incidentId) {
        try {
            var response = userClient.getUsersByRole("ADMIN");
            List<com.huynqb.laundrylocker.common.dto.UserSummary> admins = response == null ? null : response.data();
            if (admins == null) {
                return;
            }
            admins.stream()
                    .filter(admin -> admin.id() != null
                            && (admin.status() == null || "ACTIVE".equalsIgnoreCase(admin.status())))
                    .forEach(admin -> notifyStaffQuietly(admin.id(), title, message, type, incidentId));
        } catch (RuntimeException ex) {
            log.warn("Could not notify admins about incident {}: {}", incidentId, ex.getMessage());
        }
    }

    private void notifyQuietly(Long userId, String title, String message, String type, Long referenceId,
            String referenceType) {
        if (userId == null) {
            return;
        }
        try {
            notificationClient.requestNotification(
                    new NotificationRequest(userId, title, message, type, referenceId, referenceType));
        } catch (RuntimeException ignored) {
            // Incident state is authoritative; notification delivery is best effort.
        }
    }

    /// Thư, thông báo, lệnh sang service khác chỉ chạy khi giao dịch đã commit. Ngoài giao dịch (test) ⇒ chạy ngay.
    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException ex) {
                    log.warn("After-commit action of a drone incident failed: {}", ex.getMessage());
                }
            }
        });
    }

    private void inNewTransaction(Runnable action) {
        if (transactionManager == null) {
            action.run();
            return;
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> action.run());
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
