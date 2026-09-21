package com.huynqb.laundrylocker.locker.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.locker.client.IotClient;
import com.huynqb.laundrylocker.locker.client.UserClient;
import com.huynqb.laundrylocker.locker.dto.LockerReportRequest;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/// Luồng 4 (KTV tủ): định tuyến phiếu, gộp báo trùng, chặn tủ, khôi phục tài sản khi đóng phiếu.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LockerServiceTicketRoutingTest {

    private static final long LOCKER_ID = 10L;
    private static final long BOX_ID = 5L;
    private static final long CUSTOMER = 7L;
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
    @Mock private DroneMaintenanceLogRepository droneMaintenanceLogRepository;
    @Mock private IotClient iotClient;
    @Mock private UserClient userClient;
    @Mock private ReportAttachmentService attachmentService;
    @Mock private RabbitTemplate rabbitTemplate;

    private LockerService service;
    private LockerUnit locker;
    private LockerBox box;
    private final AtomicLong reportIds = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        service = new LockerService(
                lockerRepository, boxRepository, reportRepository, repairLogRepository, scheduleRepository,
                inspectionLogRepository, ratingRepository, droneUnitRepository, droneMaintenanceLogRepository,
                iotClient, userClient, attachmentService, rabbitTemplate, TestLockerRules.defaults());
        locker = new LockerUnit();
        locker.setId(LOCKER_ID);
        locker.setName("Tủ A");
        locker.setCode("CAB-A");
        locker.setStatus("ACTIVE");
        box = new LockerBox();
        box.setId(BOX_ID);
        box.setLockerId(LOCKER_ID);
        box.setBoxNumber(3);
        box.setStatus("AVAILABLE");
        when(lockerRepository.findById(LOCKER_ID)).thenReturn(Optional.of(locker));
        when(boxRepository.findById(BOX_ID)).thenReturn(Optional.of(box));
        when(reportRepository.save(any(LockerReport.class))).thenAnswer(invocation -> {
            LockerReport report = invocation.getArgument(0);
            if (report.getId() == null) {
                report.setId(reportIds.incrementAndGet());
            }
            return report;
        });
        when(repairLogRepository.save(any(RepairLog.class))).thenAnswer(invocation -> {
            RepairLog log = invocation.getArgument(0);
            log.setId(9L);
            return log;
        });
        when(userClient.getUser(TECH)).thenReturn(ApiResponse.ok(user(TECH, "LOCKER_TECHNICIAN")));
        when(userClient.getUser(OTHER_TECH)).thenReturn(ApiResponse.ok(user(OTHER_TECH, "LOCKER_TECHNICIAN")));
        when(userClient.getUser(CUSTOMER)).thenReturn(ApiResponse.ok(user(CUSTOMER, "CUSTOMER")));
    }

    @Test
    void customerFaultIsRoutedToTheLockersTechnicianAndRemembersTheOrderHoldingTheBox() {
        locker.setAssignedTechnicianId(TECH);
        box.setStatus("OCCUPIED");

        service.markFault(BOX_ID, "Cửa kẹt", CUSTOMER, List.of(), "CUSTOMER");

        LockerReport report = savedReport();
        assertEquals("OPEN", report.getStatus());
        assertNull(report.getAssignedToUserId());
        assertEquals(TECH, report.getRoutedToUserId());
        assertEquals(ReportCategory.BOX, report.getCategory());
        assertEquals("FAULT", box.getStatus());
        assertEquals("OCCUPIED", box.getPreFaultStatus());
        List<DomainEvent> routed = published(DomainEventNames.LOCKER_REPORT_ROUTED);
        assertEquals(1, routed.size());
        assertEquals(TECH, routed.get(0).payload().get("userId"));
        assertEquals(report.getId(), routed.get(0).payload().get("referenceId"));
    }

    @Test
    void lockerWithoutTechnicianNotifiesEveryLockerTechnician() {
        when(userClient.listByRole("LOCKER_TECHNICIAN"))
                .thenReturn(ApiResponse.ok(List.of(user(TECH, "LOCKER_TECHNICIAN"), user(OTHER_TECH, "LOCKER_TECHNICIAN"))));

        service.markFault(BOX_ID, "Cửa kẹt", CUSTOMER, List.of(), "CUSTOMER");

        assertNull(savedReport().getRoutedToUserId());
        assertEquals(
                Set.of(TECH, OTHER_TECH),
                Set.copyOf(published(DomainEventNames.LOCKER_REPORT_ROUTED).stream()
                        .map(event -> event.payload().get("userId")).toList()));
    }

    // Trước đây KTV được nhận ra bằng tên chứa "ktv"/"technician" — giờ chỉ theo vai trò.
    @Test
    void technicianReportIsSelfClaimedByRoleNotByName() {
        service.markFault(BOX_ID, "Hỏng khoá", TECH, List.of(), "LOCKER_TECHNICIAN");

        LockerReport report = savedReport();
        assertEquals("IN_PROGRESS", report.getStatus());
        assertEquals(TECH, report.getAssignedToUserId());
        assertTrue(published(DomainEventNames.LOCKER_REPORT_ROUTED).isEmpty());

        when(userClient.getUser(CUSTOMER))
                .thenReturn(ApiResponse.ok(new UserSummary(CUSTOMER, null, null, "KTV giả danh", "ACTIVE", Set.of("CUSTOMER"))));
        box.setStatus("AVAILABLE");
        reset(reportRepository);
        when(reportRepository.save(any(LockerReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Gọi nội bộ (order-service) không có header ⇒ tra vai trò ở user-service.
        service.markFault(BOX_ID, "Hỏng", CUSTOMER, List.of(), null);
        assertEquals("OPEN", savedReport().getStatus());
    }

    @Test
    void repeatedFaultOnABoxWithAnOpenTicketIsMergedIntoThatTicket() {
        box.setStatus("FAULT");
        LockerReport open = report(55L, "OPEN", null);
        when(reportRepository.findFirstByBoxIdAndStatusInOrderByCreatedAtDesc(eq(BOX_ID), anyList()))
                .thenReturn(Optional.of(open));
        List<ReportAttachmentRequest> photos = List.of(photo());

        service.markFault(BOX_ID, "Vẫn kẹt", CUSTOMER, photos, "CUSTOMER");

        verify(reportRepository, never()).save(any(LockerReport.class));
        ArgumentCaptor<RepairLog> log = ArgumentCaptor.forClass(RepairLog.class);
        verify(repairLogRepository).save(log.capture());
        assertEquals(55L, log.getValue().getReportId());
        assertTrue(log.getValue().getNote().contains("Vẫn kẹt"));
        verify(attachmentService).attach(open, AttachmentStage.REPORT, photos, CUSTOMER, 9L, 5);
        assertTrue(published(DomainEventNames.LOCKER_REPORT_ROUTED).isEmpty());
        assertTrue(published(DomainEventNames.LOCKER_BOX_FAULT).isEmpty());
    }

    @Test
    void onlyTheAssigneeResolvesAndATicketCannotBeResolvedTwice() {
        when(reportRepository.findById(55L)).thenReturn(Optional.of(report(55L, "IN_PROGRESS", TECH)));

        BusinessException notAssigned = assertThrows(
                BusinessException.class, () -> service.resolveReportAndClearFault(55L, OTHER_TECH, null, false));
        assertEquals("REPORT_NOT_ASSIGNED", notAssigned.getCode());
        assertEquals(HttpStatus.FORBIDDEN, notAssigned.getStatus());

        when(reportRepository.findById(56L)).thenReturn(Optional.of(report(56L, "RESOLVED", TECH)));
        BusinessException resolved = assertThrows(
                BusinessException.class, () -> service.resolveReport(56L, 1L, null));
        assertEquals("REPORT_ALREADY_RESOLVED", resolved.getCode());
    }

    @Test
    void resolvingReturnsTheBoxToWhatItWasBeforeTheFault() {
        box.setStatus("FAULT");
        box.setPreFaultStatus("OCCUPIED");
        box.setFaultReason("Kẹt");
        when(reportRepository.findById(55L)).thenReturn(Optional.of(report(55L, "IN_PROGRESS", TECH)));

        service.resolveReportAndClearFault(55L, TECH, null, false);

        assertEquals("OCCUPIED", box.getStatus());
        assertNull(box.getPreFaultStatus());
        assertNull(box.getFaultReason());
        assertEquals(1, published(DomainEventNames.LOCKER_REPORT_RESOLVED).size());
    }

    // Admin đóng phiếu từ web trước đây không trả ô về hoạt động.
    @Test
    void adminResolveAlsoRestoresTheBoxUnlessAnotherTicketStillHoldsIt() {
        box.setStatus("FAULT");
        when(reportRepository.findById(55L)).thenReturn(Optional.of(report(55L, "OPEN", null)));
        when(reportRepository.existsByBoxIdAndStatusInAndIdNot(eq(BOX_ID), anyList(), eq(55L))).thenReturn(true);

        service.resolveReport(55L, 1L, null);
        assertEquals("FAULT", box.getStatus());

        when(reportRepository.findById(57L)).thenReturn(Optional.of(report(57L, "OPEN", null)));
        service.resolveReport(57L, 1L, null);
        assertEquals("AVAILABLE", box.getStatus());
    }

    @Test
    void clearFaultGoesThroughTheOpenTicket() {
        BusinessException notFault = assertThrows(BusinessException.class, () -> service.clearFault(BOX_ID, TECH, false));
        assertEquals("BOX_NOT_FAULT", notFault.getCode());
        assertEquals(HttpStatus.CONFLICT, notFault.getStatus());

        box.setStatus("FAULT");
        LockerReport open = report(55L, "IN_PROGRESS", OTHER_TECH);
        when(reportRepository.findFirstByBoxIdAndStatusInOrderByCreatedAtDesc(eq(BOX_ID), anyList()))
                .thenReturn(Optional.of(open));
        when(reportRepository.findById(55L)).thenReturn(Optional.of(open));
        BusinessException ticketOpen = assertThrows(BusinessException.class, () -> service.clearFault(BOX_ID, TECH, false));
        assertEquals("REPORT_OPEN", ticketOpen.getCode());
        assertEquals("FAULT", box.getStatus());

        service.clearFault(BOX_ID, OTHER_TECH, false);
        assertEquals("RESOLVED", open.getStatus());
        assertEquals("AVAILABLE", box.getStatus());
    }

    @Test
    void clearFaultStillAppliesTheResolutionPhotoRule() {
        service = new LockerService(
                lockerRepository, boxRepository, reportRepository, repairLogRepository, scheduleRepository,
                inspectionLogRepository, ratingRepository, droneUnitRepository, droneMaintenanceLogRepository,
                iotClient, userClient, attachmentService, rabbitTemplate,
                TestLockerRules.of(Map.of("app.maintenance.require-resolution-photo", true)));
        box.setStatus("FAULT");
        LockerReport open = report(55L, "IN_PROGRESS", TECH);
        when(reportRepository.findFirstByBoxIdAndStatusInOrderByCreatedAtDesc(eq(BOX_ID), anyList()))
                .thenReturn(Optional.of(open));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.clearFault(BOX_ID, TECH, false));
        assertEquals("RESOLUTION_PHOTO_REQUIRED", ex.getCode());
        assertEquals("FAULT", box.getStatus());
    }

    @Test
    void faultWithoutTicketClearsStraightBackToPreviousState() {
        box.setStatus("FAULT");
        box.setPreFaultStatus("RESERVED");

        service.clearFault(BOX_ID, TECH, false);

        assertEquals("RESERVED", box.getStatus());
    }

    // Đơn đã trả ô trong lúc ô hỏng ⇒ sửa xong ô về AVAILABLE, không về OCCUPIED mồ côi.
    @Test
    void releasingAFaultyBoxForgetsTheOrderState() {
        box.setStatus("FAULT");
        box.setPreFaultStatus("OCCUPIED");

        service.releaseBox(BOX_ID);

        assertEquals("FAULT", box.getStatus());
        assertNull(box.getPreFaultStatus());
    }

    @Test
    void onlyTechniciansOrAdminsCanTakeAWholeLockerOutOfService() {
        LockerReportRequest blocking = new LockerReportRequest(null, "Mất điện", "Cả tủ tắt", List.of(), true);

        BusinessException ex = assertThrows(
                BusinessException.class, () -> service.report(LOCKER_ID, blocking, CUSTOMER, "CUSTOMER"));
        assertEquals("BLOCKING_REPORT_FORBIDDEN", ex.getCode());
        assertEquals("ACTIVE", locker.getStatus());

        service.report(LOCKER_ID, blocking, TECH, "LOCKER_TECHNICIAN");
        assertEquals("MAINTENANCE", locker.getStatus());
        assertEquals("TICKET", locker.getMaintenanceSource());
        LockerReport report = savedReport();
        assertTrue(report.getBlocksLocker());
        assertEquals(TECH, report.getAssignedToUserId());
    }

    @Test
    void closingTheLastBlockingTicketReopensTheLockerButNotAnAdminMaintenance() {
        locker.setStatus("MAINTENANCE");
        locker.setMaintenanceSource("TICKET");
        LockerReport blocking = report(60L, "IN_PROGRESS", TECH);
        blocking.setBoxId(null);
        blocking.setCategory(ReportCategory.LOCKER);
        blocking.setBlocksLocker(true);
        when(reportRepository.findById(60L)).thenReturn(Optional.of(blocking));

        service.resolveReportAndClearFault(60L, TECH, null, false);
        assertEquals("ACTIVE", locker.getStatus());
        assertNull(locker.getMaintenanceSource());

        locker.setStatus("MAINTENANCE");
        locker.setMaintenanceSource("ADMIN");
        LockerReport second = report(61L, "IN_PROGRESS", TECH);
        second.setBoxId(null);
        second.setBlocksLocker(true);
        when(reportRepository.findById(61L)).thenReturn(Optional.of(second));
        service.resolveReportAndClearFault(61L, TECH, null, false);
        assertEquals("MAINTENANCE", locker.getStatus());
    }

    @Test
    void lockerUnderMaintenanceTakesNoBookings() {
        locker.setStatus("MAINTENANCE");

        BusinessException reserve = assertThrows(BusinessException.class, () -> service.reserveBox(BOX_ID, "CUSTOMER"));
        assertEquals("LOCKER_NOT_ACTIVE", reserve.getCode());
        BusinessException find = assertThrows(
                BusinessException.class, () -> service.findAvailableBox(LOCKER_ID, null, "STANDARD"));
        assertEquals("LOCKER_NOT_ACTIVE", find.getCode());
        assertTrue(service.listAvailableBoxes(LOCKER_ID).isEmpty());
        verify(boxRepository, never()).findByLockerIdAndStatusAndActiveTrue(anyLong(), anyString());
        assertEquals("AVAILABLE", box.getStatus());
    }

    @Test
    void adminAssignmentChecksRoleAndNotifiesTheTechnician() {
        when(reportRepository.findById(55L)).thenReturn(Optional.of(report(55L, "RESOLVED", null)));
        assertEquals(
                "REPORT_ALREADY_RESOLVED",
                assertThrows(BusinessException.class, () -> service.assignReport(55L, TECH)).getCode());

        when(reportRepository.findById(56L)).thenReturn(Optional.of(report(56L, "OPEN", null)));
        assertEquals(
                "TECHNICIAN_ROLE_REQUIRED",
                assertThrows(BusinessException.class, () -> service.assignReport(56L, CUSTOMER)).getCode());

        service.assignReport(56L, TECH);
        List<DomainEvent> assigned = published(DomainEventNames.LOCKER_REPORT_ASSIGNED);
        assertEquals(1, assigned.size());
        assertEquals(TECH, assigned.get(0).payload().get("userId"));
    }

    @Test
    void assigningALockerTechnicianReroutesWaitingTickets() {
        LockerReport waiting = report(55L, "OPEN", null);
        LockerReport drone = report(56L, "OPEN", null);
        drone.setCategory(ReportCategory.DRONE);
        when(reportRepository.findByLockerIdAndStatusAndAssignedToUserIdIsNull(LOCKER_ID, "OPEN"))
                .thenReturn(List.of(waiting, drone));
        when(lockerRepository.save(any(LockerUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.assignLockerTechnician(LOCKER_ID, TECH);

        assertEquals(TECH, locker.getAssignedTechnicianId());
        assertEquals(TECH, waiting.getRoutedToUserId());
        assertNull(drone.getRoutedToUserId());
        assertEquals(TECH, response.assignedTechnicianId());
        assertEquals(1, published(DomainEventNames.LOCKER_REPORT_ASSIGNED).size());

        assertEquals(
                "TECHNICIAN_ROLE_REQUIRED",
                assertThrows(BusinessException.class, () -> service.assignLockerTechnician(LOCKER_ID, CUSTOMER))
                        .getCode());
    }

    @Test
    void landingPadTicketOpensOnceAndClosingItRestoresThePad() {
        locker.setLandingPad(true);

        service.updateLandingPadStatus(LOCKER_ID, "FAULT", "Nứt mặt đáp", TECH, "LOCKER_TECHNICIAN");
        LockerReport pad = savedReport();
        assertEquals(ReportCategory.LANDING_PAD, pad.getCategory());
        assertEquals(TECH, pad.getAssignedToUserId());
        assertEquals("FAULT", locker.getLandingPadStatus());

        when(reportRepository.findFirstByLockerIdAndCategoryAndStatusInOrderByCreatedAtDesc(
                eq(LOCKER_ID), eq(ReportCategory.LANDING_PAD), anyList())).thenReturn(Optional.of(pad));
        clearInvocations(reportRepository);
        service.updateLandingPadStatus(LOCKER_ID, "MAINTENANCE", null, TECH, "LOCKER_TECHNICIAN");
        verify(reportRepository, never()).save(any(LockerReport.class));

        service.updateLandingPadStatus(LOCKER_ID, "OK", null, TECH, "LOCKER_TECHNICIAN");
        assertEquals("RESOLVED", pad.getStatus());
        assertEquals("OK", locker.getLandingPadStatus());
    }

    private LockerReport savedReport() {
        ArgumentCaptor<LockerReport> saved = ArgumentCaptor.forClass(LockerReport.class);
        verify(reportRepository, atLeastOnce()).save(saved.capture());
        return saved.getValue();
    }

    private List<DomainEvent> published(String type) {
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, atLeast(0)).convertAndSend(eq(DomainEventNames.EXCHANGE), anyString(), events.capture());
        return events.getAllValues().stream()
                .map(DomainEvent.class::cast)
                .filter(event -> type.equals(event.type()))
                .toList();
    }

    private static LockerReport report(Long id, String status, Long assignee) {
        LockerReport report = new LockerReport();
        report.setId(id);
        report.setLockerId(LOCKER_ID);
        report.setBoxId(BOX_ID);
        report.setCategory(ReportCategory.BOX);
        report.setUserId(CUSTOMER);
        report.setTitle("Box 3 fault");
        report.setDescription("Kẹt");
        report.setStatus(status);
        report.setAssignedToUserId(assignee);
        return report;
    }

    private static UserSummary user(Long id, String role) {
        return new UserSummary(id, null, null, "User " + id, "ACTIVE", Set.of(role));
    }

    private static ReportAttachmentRequest photo() {
        return new ReportAttachmentRequest(
                "lockr/reports/u7/a", 1L, "sig", "jpg", 1L, 1, 1, null, null, null, null);
    }
}
