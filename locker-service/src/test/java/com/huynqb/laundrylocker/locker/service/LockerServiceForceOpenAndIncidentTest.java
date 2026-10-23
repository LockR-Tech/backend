package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.locker.client.IotClient;
import com.huynqb.laundrylocker.locker.client.OrderClient;
import com.huynqb.laundrylocker.locker.client.UserClient;
import com.huynqb.laundrylocker.locker.dto.ActiveBoxOrderDto;
import com.huynqb.laundrylocker.locker.dto.BoxIncidentResolutionRequest;
import com.huynqb.laundrylocker.locker.dto.ForceOpenRequest;
import com.huynqb.laundrylocker.locker.dto.LockerResponse;
import com.huynqb.laundrylocker.locker.dto.ReportAttachmentRequest;
import com.huynqb.laundrylocker.locker.model.AttachmentStage;
import com.huynqb.laundrylocker.locker.model.LockerBox;
import com.huynqb.laundrylocker.locker.model.LockerReport;
import com.huynqb.laundrylocker.locker.model.LockerUnit;
import com.huynqb.laundrylocker.locker.model.RepairLog;
import com.huynqb.laundrylocker.locker.model.ReportCategory;
import com.huynqb.laundrylocker.locker.repository.*;
import com.huynqb.laundrylocker.locker.settings.TestLockerRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/// Mở ô khẩn cấp có lý do (+ nhật ký phiếu), xử lý sự cố ô tại hiện trường, cờ `online` của tủ.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LockerServiceForceOpenAndIncidentTest {

    private static final long LOCKER_ID = 10L;
    private static final long BOX_ID = 5L;
    private static final long TECH = 42L;
    private static final long OTHER_TECH = 43L;

    @Mock private LockerUnitRepository lockerRepository;
    @Mock private LockerBoxRepository boxRepository;
    @Mock private LockerReportRepository reportRepository;
    @Mock private RepairLogRepository repairLogRepository;
    @Mock private MaintenanceScheduleRepository scheduleRepository;
    @Mock private MaintenanceInspectionLogRepository inspectionLogRepository;
    @Mock private LockerReportRatingRepository ratingRepository;
    @Mock private DroneUnitRepository droneUnitRepository;
    @Mock private DroneDeviceRepository droneDeviceRepository;
    @Mock private DroneMaintenanceLogRepository droneMaintenanceLogRepository;
    @Mock private DroneIotLogRepository droneIotLogRepository;
    @Mock private IotClient iotClient;
    @Mock private UserClient userClient;
    @Mock private OrderClient orderClient;
    @Mock private ReportAttachmentService attachmentService;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private TechnicianPresenceRepository presenceRepository;

    private LockerService service;
    private LockerUnit locker;
    private LockerBox box;
    private LockerReport report;

    @BeforeEach
    void setUp() {
        service = new LockerService(
                lockerRepository, boxRepository, reportRepository, repairLogRepository, scheduleRepository,
                inspectionLogRepository, ratingRepository, droneUnitRepository, droneDeviceRepository, droneMaintenanceLogRepository,
                droneIotLogRepository,
                iotClient, userClient, orderClient, attachmentService, rabbitTemplate, TestLockerRules.defaults(),
                presenceRepository);
        locker = new LockerUnit();
        locker.setId(LOCKER_ID);
        locker.setName("Tủ A");
        locker.setCode("CAB-A");
        locker.setStatus("ACTIVE");
        box = new LockerBox();
        box.setId(BOX_ID);
        box.setLockerId(LOCKER_ID);
        box.setBoxNumber(3);
        box.setStatus("OCCUPIED");
        report = new LockerReport();
        report.setId(100L);
        report.setLockerId(LOCKER_ID);
        report.setBoxId(BOX_ID);
        report.setUserId(7L);
        report.setTitle("Ô #3 báo hỏng");
        report.setDescription("Cửa kẹt");
        report.setCategory(ReportCategory.BOX);
        report.setStatus("IN_PROGRESS");
        report.setAssignedToUserId(TECH);
        report.setCreatedAt(LocalDateTime.now());
        when(lockerRepository.findById(LOCKER_ID)).thenReturn(Optional.of(locker));
        when(boxRepository.findById(BOX_ID)).thenReturn(Optional.of(box));
        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(LockerReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(boxRepository.save(any(LockerBox.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repairLogRepository.save(any(RepairLog.class))).thenAnswer(invocation -> {
            RepairLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(9L);
            }
            return log;
        });
        when(attachmentService.byReportIds(any())).thenReturn(Map.of());
    }

    // ─── Mở ô khẩn cấp ───

    @Test
    void reasonAndNoteAreForwardedToIot() {
        when(iotClient.forceUnlock(any())).thenReturn(ApiResponse.ok(Map.of("accepted", true, "status", "OPENED")));

        service.forceOpen(BOX_ID, TECH, new ForceOpenRequest("Khách kẹt đồ", "Đã gọi xác nhận", null, null), false);

        ArgumentCaptor<IotClient.ForceUnlockRequest> sent = ArgumentCaptor.forClass(IotClient.ForceUnlockRequest.class);
        verify(iotClient).forceUnlock(sent.capture());
        assertEquals("Khách kẹt đồ", sent.getValue().reason());
        assertEquals("Đã gọi xác nhận", sent.getValue().note());
        verifyNoInteractions(repairLogRepository);
    }

    @Test
    void requestWithoutBodyKeepsLegacyBehaviour() {
        when(iotClient.forceUnlock(any())).thenReturn(ApiResponse.ok(Map.of("accepted", true, "status", "OPENED")));

        Map<String, Object> result = service.forceOpen(BOX_ID, TECH);

        assertEquals(true, result.get("accepted"));
        ArgumentCaptor<IotClient.ForceUnlockRequest> sent = ArgumentCaptor.forClass(IotClient.ForceUnlockRequest.class);
        verify(iotClient).forceUnlock(sent.capture());
        assertNull(sent.getValue().reason());
    }

    @Test
    void withReportTheOutcomeIsLoggedOnTheTicketWithPhotos() {
        List<ReportAttachmentRequest> photos = List.of(photo());
        when(iotClient.forceUnlock(any())).thenReturn(ApiResponse.ok(Map.of(
                "accepted", false, "status", "TIMEOUT", "message", "Tủ không phản hồi")));

        Map<String, Object> result = service.forceOpen(
                BOX_ID, TECH, new ForceOpenRequest("Khách kẹt đồ", null, photos, 100L), false);

        assertEquals(100L, result.get("reportId"));
        assertEquals(9L, result.get("repairLogId"));
        verify(attachmentService).assertCanAttachAsStaff(report, TECH, false);
        verify(attachmentService).attach(eq(report), eq(AttachmentStage.PROGRESS), eq(photos), eq(TECH), eq(9L), anyInt());
        ArgumentCaptor<RepairLog> logs = ArgumentCaptor.forClass(RepairLog.class);
        verify(repairLogRepository, times(2)).save(logs.capture());
        String note = logs.getValue().getNote();
        assertTrue(note.startsWith("[MỞ KHẨN CẤP] Ô #3 — Không mở được ô (TIMEOUT)"), note);
        assertTrue(note.contains("Lý do: Khách kẹt đồ"), note);
    }

    @Test
    void photosWithoutReportAreRejectedBeforeOpening() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.forceOpen(
                BOX_ID, TECH, new ForceOpenRequest("Lý do", null, List.of(photo()), null), false));

        assertEquals("FORCE_OPEN_REPORT_REQUIRED", ex.getCode());
        verifyNoInteractions(iotClient);
    }

    @Test
    void reportOfAnotherLockerIsRejectedBeforeOpening() {
        report.setLockerId(99L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.forceOpen(
                BOX_ID, TECH, new ForceOpenRequest("Lý do", null, null, 100L), false));

        assertEquals("FORCE_OPEN_REPORT_MISMATCH", ex.getCode());
        verifyNoInteractions(iotClient);
    }

    // ─── Xử lý sự cố ô ───

    @Test
    void onlyAssignedTechnicianResolvesBoxIncident() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.resolveBoxIncident(
                100L, incident("LOCK_ONLY", null), OTHER_TECH, "LOCKER_TECHNICIAN"));

        assertEquals("REPORT_NOT_ASSIGNED", ex.getCode());
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(boxRepository, never()).save(any());
    }

    @Test
    void adminMayResolveBoxIncident() {
        service.resolveBoxIncident(100L, incident("LOCK_ONLY", null), 1L, "ADMIN");

        assertEquals("FAULT", box.getStatus());
    }

    @Test
    void hubEscrowRequiresSealEnteredByTechnician() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.resolveBoxIncident(
                100L, incident("HUB_ESCROW", "  "), TECH, "LOCKER_TECHNICIAN"));

        assertEquals("SEAL_NUMBER_REQUIRED", ex.getCode());
        verifyNoInteractions(orderClient);
    }

    @Test
    void hubEscrowUsesTheGivenSeal() {
        when(orderClient.getActiveOrderByBox(BOX_ID))
                .thenReturn(ApiResponse.ok(new ActiveBoxOrderDto(55L, "ORD-55", 7L, "Khách A", "0900", "SEND", "STORING", null, BOX_ID)));

        service.resolveBoxIncident(100L, incident("HUB_ESCROW", " SEAL-778 "), TECH, "LOCKER_TECHNICIAN");

        verify(orderClient).hubEscrow(55L, "SEAL-778");
    }

    @Test
    void unknownIncidentActionIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.resolveBoxIncident(
                100L, incident("DESTROY", null), TECH, "LOCKER_TECHNICIAN"));

        assertEquals("INCIDENT_ACTION_INVALID", ex.getCode());
        verify(boxRepository, never()).save(any());
    }

    @Test
    void boxMustBelongToTheReportsLocker() {
        box.setLockerId(99L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.resolveBoxIncident(
                100L, incident("LOCK_ONLY", null), TECH, "LOCKER_TECHNICIAN"));

        assertEquals("INCIDENT_BOX_NOT_IN_LOCKER", ex.getCode());
    }

    @Test
    void handoverPassesCustomerOtpThrough() {
        when(orderClient.getActiveOrderByBox(BOX_ID))
                .thenReturn(ApiResponse.ok(new ActiveBoxOrderDto(55L, "ORD-55", 7L, "Khách A", "0900", "SEND", "STORING", null, BOX_ID)));

        service.resolveBoxIncident(100L, new BoxIncidentResolutionRequest(
                BOX_ID, "HANDOVER", null, null, "123456", null, null), TECH, "LOCKER_TECHNICIAN");

        verify(orderClient).directHandover(55L, "123456");
    }

    @Test
    void lockOnlyWithoutLockingPreservesOccupiedWhenBoxHoldsOrder() {
        box.setStatus("OCCUPIED");
        when(orderClient.getActiveOrderByBox(BOX_ID))
                .thenReturn(ApiResponse.ok(new ActiveBoxOrderDto(55L, "ORD-55", 7L, "Khách A", "0900", "SEND", "STORING", null, BOX_ID)));

        service.resolveBoxIncident(100L, new BoxIncidentResolutionRequest(
                BOX_ID, "LOCK_ONLY", null, null, null, null, null, false), TECH, "LOCKER_TECHNICIAN");

        assertEquals("OCCUPIED", box.getStatus());
        verify(boxRepository).save(box);
    }

    /// Sửa tại chỗ (không khoá ô) hoặc kiểm tra thấy ô vẫn chạy ⇒ khách lấy đồ được: order-service phải
    /// mở lại đơn (bỏ phí trong thời gian chờ). Khoá ô thì đồ vẫn kẹt, chưa được báo.
    @Test
    void fixingTheBoxInPlaceResumesTheStuckOrder() {
        box.setStatus("OCCUPIED");
        when(orderClient.getActiveOrderByBox(BOX_ID))
                .thenReturn(ApiResponse.ok(new ActiveBoxOrderDto(55L, "ORD-55", 7L, "Khách A", "0900", "RENTAL", "STORING", null, BOX_ID)));

        service.resolveBoxIncident(100L, new BoxIncidentResolutionRequest(
                BOX_ID, "QUICK_FIX", null, null, null, null, null, false), TECH, "LOCKER_TECHNICIAN");

        verify(orderClient).boxIncidentResolved(eq(55L), any());
    }

    @Test
    void lockingTheBoxKeepsTheOrderWaiting() {
        box.setStatus("OCCUPIED");
        when(orderClient.getActiveOrderByBox(BOX_ID))
                .thenReturn(ApiResponse.ok(new ActiveBoxOrderDto(55L, "ORD-55", 7L, "Khách A", "0900", "RENTAL", "STORING", null, BOX_ID)));

        service.resolveBoxIncident(100L, new BoxIncidentResolutionRequest(
                BOX_ID, "LOCK_ONLY", null, null, null, null, null, true), TECH, "LOCKER_TECHNICIAN");

        verify(orderClient, never()).boxIncidentResolved(any(), any());
    }

    /// order-service không trả lời: không được coi ô là trống — ô còn đồ của khách sẽ bị đặt
    /// AVAILABLE và cấp cho đơn khác. Dừng với 503, không đổi ô nào.
    @Test
    void incidentResolutionStopsWhenActiveOrderCannotBeChecked() {
        box.setStatus("FAULT");
        when(orderClient.getActiveOrderByBox(BOX_ID)).thenThrow(new RuntimeException("order-service down"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.resolveBoxIncident(
                100L, new BoxIncidentResolutionRequest(BOX_ID, "QUICK_FIX", null, null, null, null, null, false),
                TECH, "LOCKER_TECHNICIAN"));

        assertEquals("ORDER_LOOKUP_UNAVAILABLE", ex.getCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertEquals("FAULT", box.getStatus());
        verify(boxRepository, never()).save(any(LockerBox.class));
    }

    @Test
    void manualBoxStatusChangeStopsWhenActiveOrderCannotBeChecked() {
        box.setStatus("FAULT");
        when(orderClient.getActiveOrderByBox(BOX_ID)).thenThrow(new RuntimeException("order-service down"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateBoxStatus(BOX_ID, "AVAILABLE"));

        assertEquals("ORDER_LOOKUP_UNAVAILABLE", ex.getCode());
        verify(boxRepository, never()).save(any(LockerBox.class));
    }

    @Test
    void updateBoxStatusThrowsWhenBoxHoldsOrder() {
        box.setStatus("OCCUPIED");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateBoxStatus(BOX_ID, "AVAILABLE"));

        assertEquals("BOX_IN_USE", ex.getCode());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void updateBoxStatusThrowsWhenTargetStatusIsOrderManaged() {
        box.setStatus("AVAILABLE");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateBoxStatus(BOX_ID, "OCCUPIED"));

        assertEquals("CANNOT_MANUALLY_ASSIGN_ORDER_STATUS", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    // ─── Cờ online của tủ ───

    @Test
    void lockerListCarriesOnlineFlagFromOneGatewayCall() {
        LockerUnit other = new LockerUnit();
        other.setId(11L);
        other.setName("Tủ B");
        other.setCode("CAB-B");
        other.setStatus("ACTIVE");
        when(lockerRepository.findAll()).thenReturn(List.of(locker, other));
        when(iotClient.listGateways()).thenReturn(ApiResponse.ok(List.of(gateway(LOCKER_ID, true))));

        List<LockerResponse> lockers = service.listLockers(null);

        assertEquals(Boolean.TRUE, lockers.get(0).online());
        // Tủ B chưa gắn bộ điều khiển: chưa biết (null), không phải mất kết nối.
        assertNull(lockers.get(1).online());
        verify(iotClient, times(1)).listGateways();
        verify(iotClient, never()).getGatewayByLockerId(any());
    }

    @Test
    void lockerWithOfflineControllerIsReportedOffline() {
        when(lockerRepository.findAll()).thenReturn(List.of(locker));
        when(iotClient.listGateways()).thenReturn(ApiResponse.ok(List.of(gateway(LOCKER_ID, false))));

        assertEquals(Boolean.FALSE, service.listLockers(null).getFirst().online());
    }

    @Test
    void lockerListStillLoadsWhenIotIsDown() {
        when(lockerRepository.findAll()).thenReturn(List.of(locker));
        when(iotClient.listGateways()).thenThrow(new RuntimeException("iot-service down"));

        List<LockerResponse> lockers = service.listLockers(null);

        assertEquals(1, lockers.size());
        assertNull(lockers.getFirst().online());
    }

    @Test
    void singleLockerUsesSameOnlineSourceAsLayout() {
        when(iotClient.getGatewayByLockerId(LOCKER_ID)).thenReturn(ApiResponse.ok(gateway(LOCKER_ID, true)));

        assertTrue(service.getLocker(LOCKER_ID).online());
        assertEquals(Boolean.TRUE, service.layout(LOCKER_ID).online());
    }

    private BoxIncidentResolutionRequest incident(String action, String seal) {
        return new BoxIncidentResolutionRequest(BOX_ID, action, null, "Ghi chú", null, seal, null);
    }

    private static IotClient.GatewayDeviceResponse gateway(Long lockerId, boolean online) {
        return new IotClient.GatewayDeviceResponse(
                1L, "2C:CF:67:DB:C5:C3", "gpio", "v1.0.0", 1, 7, lockerId, lockerId, online, "COMPLETED", "7/7",
                LocalDateTime.now());
    }

    private static ReportAttachmentRequest photo() {
        return new ReportAttachmentRequest(
                "lockr/reports/abc", 1L, "sig", "jpg", 1000L, 800, 600, null, null, null, null);
    }
}
