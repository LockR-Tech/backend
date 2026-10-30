package com.huynqb.laundrylocker.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.DroneIncidentCustomerResponseRequest;
import com.huynqb.laundrylocker.order.dto.DroneIncidentProposalRequest;
import com.huynqb.laundrylocker.order.dto.DroneParcelIncidentResponse;
import com.huynqb.laundrylocker.order.dto.DroneTelemetryFrame;
import com.huynqb.laundrylocker.order.dto.DroneRecoverySubmitRequest;
import com.huynqb.laundrylocker.order.dto.DroneRecoveryUpdateRequest;
import com.huynqb.laundrylocker.order.dto.ReturnFlightUpdateRequest;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.DroneIncidentResolutionProposal;
import com.huynqb.laundrylocker.order.model.DroneParcelIncident;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DroneParcelIncidentServiceTest {
    @Mock DroneParcelIncidentRepository incidentRepository;
    @Mock DroneIncidentEvidenceRepository evidenceRepository;
    @Mock DroneIncidentTimelineRepository timelineRepository;
    @Mock DroneIncidentResolutionProposalRepository proposalRepository;
    @Mock LockerOrderRepository orderRepository;
    @Mock DroneMissionRepository missionRepository;
    @Mock OrderStatusHistoryRepository orderHistoryRepository;
    @Mock DroneTelemetryRegistry telemetryRegistry;
    @Mock LockerDroneClient lockerDroneClient;
    @Mock com.huynqb.laundrylocker.order.client.LockerLookupClient lockerLookupClient;
    @Mock UserClient userClient;
    @Mock NotificationClient notificationClient;
    @Mock DroneReturnCommandGateway returnCommandGateway;
    @Mock IncidentCompensationGateway compensationGateway;
    @Mock IncidentRedeliveryGateway redeliveryGateway;
    @Mock DronePickupService dronePickupService;
    @Mock CloudinaryMediaStorage mediaStorage;
    @Mock com.huynqb.laundrylocker.order.client.LockerClient lockerClient;
    @Mock com.huynqb.laundrylocker.order.settings.OrderRules rules;

    private DroneParcelIncidentService service;

    @BeforeEach
    void setUp() {
        service = new DroneParcelIncidentService(
                incidentRepository, evidenceRepository, timelineRepository, proposalRepository,
                orderRepository, missionRepository, orderHistoryRepository, telemetryRegistry,
                lockerDroneClient, lockerLookupClient, userClient, notificationClient, returnCommandGateway,
                compensationGateway, redeliveryGateway, dronePickupService, mediaStorage, new ObjectMapper(), rules,
                lockerClient);
        lenient().when(rules.incidentPolicy()).thenReturn(new com.huynqb.laundrylocker.order.settings.OrderRules.IncidentPolicy(
                "v1", true, BigDecimal.valueOf(60), BigDecimal.ZERO, true, true, 4, true, true));
        lenient().when(evidenceRepository.findByIncidentIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        lenient().when(timelineRepository.findByIncidentIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        lenient().when(proposalRepository.findByIncidentIdOrderByProposalVersionAsc(any())).thenReturn(List.of());
        lenient().when(incidentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recoveryCannotStartBeforeTechnicianAccepts() {
        DroneParcelIncident incident = incident("ASSIGNED");
        when(incidentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(incident));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.recoveryAction(7L, 44L, new DroneRecoveryUpdateRequest("START_SEARCHING")));

        assertEquals("RECOVERY_NOT_STARTABLE", error.getCode());
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void recoverySubmissionRequiresRealPhotoEvidence() {
        DroneParcelIncident incident = incident("SEARCHING");
        when(incidentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(incident));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submitRecovery(7L, 44L,
                        new DroneRecoverySubmitRequest("FOUND", "INTACT", null,
                                10.1, 106.2, 8D, List.of())));

        assertEquals("RECOVERY_EVIDENCE_REQUIRED", error.getCode());
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void compensationUsesOrderPolicySnapshotAndRequiresReasonAboveSuggestedAmount() {
        DroneParcelIncident incident = incident("VERIFIED");
        LockerOrder order = policyOrder();
        when(incidentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(incident));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(proposalRepository.findFirstByIncidentIdOrderByProposalVersionDesc(7L)).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.propose(7L, 1L,
                        new DroneIncidentProposalRequest("COMPENSATION_ONLY", false,
                                new BigDecimal("601.00"), false, null, null)));
        assertEquals("COMPENSATION_OVERRIDE_REASON_REQUIRED", error.getCode());

        service.propose(7L, 1L,
                new DroneIncidentProposalRequest("COMPENSATION_ONLY", false,
                        new BigDecimal("600.00"), false, null, null));

        ArgumentCaptor<DroneIncidentResolutionProposal> proposal =
                ArgumentCaptor.forClass(DroneIncidentResolutionProposal.class);
        verify(proposalRepository).save(proposal.capture());
        assertEquals("policy-2026-10", proposal.getValue().getPolicyVersion());
        assertEquals(new BigDecimal("600.00"), proposal.getValue().getCompensationAmount());
        assertEquals(1, proposal.getValue().getProposalVersion());
    }

    @Test
    void incidentResponseCarriesSuggestedCompensationOnlyWhenPolicyEnabled() {
        DroneParcelIncident incident = incident("VERIFIED");
        LockerOrder order = policyOrder();
        order.setIncidentCompensationCap(new BigDecimal("500.00"));
        when(incidentRepository.findById(7L)).thenReturn(Optional.of(incident));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));

        // 1000 × 60% = 600, chặn trần 500.
        assertEquals(new BigDecimal("500.00"), service.get(7L, 1L, "ADMIN").suggestedCompensation());

        order.setIncidentCompensationEnabled(false);
        assertNull(service.get(7L, 1L, "ADMIN").suggestedCompensation());
    }

    @Test
    void customerReviewRequestPreservesProposalAndOpensDispute() {
        DroneParcelIncident incident = incident("VERIFIED");
        incident.setStatus("AWAITING_CUSTOMER_RESPONSE");
        LockerOrder order = policyOrder();
        order.setUserId(55L);
        DroneIncidentResolutionProposal proposal = new DroneIncidentResolutionProposal();
        proposal.setIncidentId(7L);
        proposal.setProposalVersion(2);
        proposal.setStatus("AWAITING_CUSTOMER_ACCEPTANCE");
        proposal.setCompensationAmount(new BigDecimal("600.00"));
        when(incidentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(incident));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(proposalRepository.findFirstByIncidentIdOrderByProposalVersionDesc(7L))
                .thenReturn(Optional.of(proposal));

        service.customerRespond(7L, 55L,
                new DroneIncidentCustomerResponseRequest("REQUEST_REVIEW", "Kiểm tra lại mức bồi thường"));

        assertEquals("DISPUTED", incident.getStatus());
        assertEquals("DISPUTED", incident.getCompensationStatus());
        assertEquals("DISPUTED", proposal.getStatus());
        assertEquals(2, proposal.getProposalVersion());
        verify(proposalRepository).save(proposal);
    }

    @Test
    void cameraReportsUnavailableInsteadOfInventingAStream() {
        LockerOrder order = policyOrder();
        order.setType("DRONE_DELIVERY");
        order.setDeliveryStage("EN_ROUTE");
        order.setFulfillmentMode("DEMO");
        DroneMission mission = new DroneMission();
        mission.setOrderId(21L);
        mission.setDroneCode("DRONE-09");
        mission.setAssignedByUserId(99L);
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));
        when(telemetryRegistry.latest("DRONE-09")).thenReturn(Optional.empty());
        when(telemetryRegistry.isLive("DRONE-09")).thenReturn(false);

        Map<String, Object> camera = service.camera(21L, 99L, "DRONE_TECHNICIAN");

        assertEquals("UNAVAILABLE", camera.get("status"));
        assertEquals(false, camera.get("integrationAvailable"));
        assertEquals(DroneParcelIncidentService.FPT_HCM_LATITUDE, camera.get("latitude"));
        assertEquals(DroneParcelIncidentService.FPT_HCM_LONGITUDE, camera.get("longitude"));
        assertEquals("FLIGHT_PATH", camera.get("locationSource"));
        assertFalse(camera.containsKey("streamUrl"));
    }

    @Test
    void cameraWithoutStreamStillCarriesLatestTelemetry() {
        LockerOrder order = policyOrder();
        order.setType("DRONE_DELIVERY");
        order.setDeliveryStage("EN_ROUTE");
        DroneMission mission = new DroneMission();
        mission.setOrderId(21L);
        mission.setDroneCode("DRONE-09");
        mission.setAssignedByUserId(99L);
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));
        when(telemetryRegistry.latest("DRONE-09")).thenReturn(Optional.of(new DroneTelemetryFrame(
                1, "DRONE-09", 7L, "2026-10-11T03:00:00Z",
                new DroneTelemetryFrame.Link("connected", 200L),
                new DroneTelemetryFrame.Position(10.85, 106.77, 32.5, 90.0, 200L, false),
                new DroneTelemetryFrame.Gps(3, 14, 200L, false),
                new DroneTelemetryFrame.Battery(76, 15.4, 3.1, 200L, false),
                new DroneTelemetryFrame.Velocity(8.2, 0.1, 200L, false),
                new DroneTelemetryFrame.Flight("AUTO", true, "ACTIVE", 200L, false),
                new DroneTelemetryFrame.Landed("IN_AIR", 200L, false))));
        when(telemetryRegistry.isLive("DRONE-09")).thenReturn(true);

        Map<String, Object> camera = service.camera(21L, 99L, "DRONE_TECHNICIAN");

        assertEquals("UNAVAILABLE", camera.get("status"));
        assertEquals(true, camera.get("telemetryAvailable"));
        assertEquals(76, camera.get("batteryPercent"));
        assertEquals("AUTO", camera.get("flightMode"));
        assertEquals(10.85, camera.get("latitude"));
        assertEquals(106.77, camera.get("longitude"));
        assertEquals(true, camera.get("gpsFix"));
        assertEquals(14, camera.get("satellites"));
        assertEquals("2026-10-11T03:00:00Z", camera.get("observedAt"));
    }

    @Test
    void droneTechnicianIncidentListRequiresDroneTechnicianRole() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.listForDroneTechnician(99L, "CUSTOMER"));

        assertEquals("DRONE_TECHNICIAN_REQUIRED", error.getCode());
        verifyNoInteractions(incidentRepository);
    }

    @Test
    void droneTechnicianIncidentListShowsDropReportedOrderStageAndDisputePolicy() {
        LockerOrder order = policyOrder();
        order.setStatus("AWAITING_DISPATCH");
        order.setDeliveryStage("DROP_REPORTED");
        when(incidentRepository.findVisibleToDroneTechnician(99L)).thenReturn(List.of(incident("ASSIGNED")));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));

        List<DroneParcelIncidentResponse> result = service.listForDroneTechnician(99L, "DRONE_TECHNICIAN");

        assertEquals(1, result.size());
        assertEquals("DROP_REPORTED", result.getFirst().deliveryStage());
        assertEquals("AWAITING_DISPATCH", result.getFirst().orderStatus());
        assertTrue(result.getFirst().disputeAllowed());
    }

    @Test
    void droneTechnicianDetailAllowsTheMissionOperatorEvenIfAdminReported() {
        DroneParcelIncident incident = incident("ASSIGNED");
        incident.setReportedByUserId(1L);
        incident.setRecoveryAssignedToUserId(44L);
        DroneMission mission = new DroneMission();
        mission.setAssignedByUserId(99L);
        when(incidentRepository.findById(7L)).thenReturn(Optional.of(incident));
        when(missionRepository.findById(31L)).thenReturn(Optional.of(mission));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(policyOrder()));

        assertEquals("DPI-21-1", service.getForDroneTechnician(7L, 99L, "DRONE_TECHNICIAN").incidentCode());
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getForDroneTechnician(7L, 77L, "DRONE_TECHNICIAN"));
        assertEquals("INCIDENT_FORBIDDEN", error.getCode());
    }

    @Test
    void customerIncidentResponseReportsWhetherReviewCanBeRequested() {
        LockerOrder order = policyOrder();
        order.setUserId(55L);
        when(incidentRepository.findById(7L)).thenReturn(Optional.of(incident("VERIFIED")));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));

        assertTrue(service.get(7L, 55L, "CUSTOMER").disputeAllowed());
        order.setIncidentDisputeAllowed(false);
        assertFalse(service.get(7L, 55L, "CUSTOMER").disputeAllowed());
    }

    @Test
    void cameraRejectsAssignedUserWithoutDroneTechnicianRole() {
        LockerOrder order = policyOrder();
        DroneMission mission = new DroneMission();
        mission.setDroneCode("DRONE-09");
        mission.setAssignedByUserId(99L);
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.camera(21L, 99L, "CUSTOMER"));

        assertEquals("DRONE_JOURNEY_FORBIDDEN", error.getCode());
        verifyNoInteractions(telemetryRegistry);
    }

    @Test
    void idempotencyKeyCannotBeReusedForAnotherOrder() {
        DroneParcelIncident replay = incident("ASSIGNED");
        replay.setIdempotencyKey("drop-key");
        when(incidentRepository.findByIdempotencyKey("drop-key")).thenReturn(Optional.of(replay));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.report(22L, 1L, "ADMIN", "drop-key", null));

        assertEquals("IDEMPOTENCY_KEY_REUSED", error.getCode());
        verify(orderRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void compensationApprovalDoesNotPretendUnavailablePayoutSucceeded() {
        DroneParcelIncident incident = incident("VERIFIED");
        incident.setCompensationStatus("AWAITING_APPROVAL");
        LockerOrder order = policyOrder();
        order.setUserId(55L);
        DroneIncidentResolutionProposal proposal = new DroneIncidentResolutionProposal();
        proposal.setProposalVersion(3);
        proposal.setStatus("ACCEPTED");
        proposal.setCompensationAmount(new BigDecimal("600.00"));
        when(incidentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(incident));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(proposalRepository.findFirstByIncidentIdOrderByProposalVersionDesc(7L))
                .thenReturn(Optional.of(proposal));
        when(compensationGateway.submit(any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(new IncidentCompensationGateway.Submission(
                        false, false, null, "integration unavailable"));

        service.approveCompensation(7L, 1L, "ADMIN");

        assertEquals("PAYMENT_PENDING", incident.getCompensationStatus());
        assertNull(incident.getCompensationReference());
        verify(compensationGateway).submit(7L, 21L, 55L, new BigDecimal("600.00"),
                false, "drone-incident-compensation-7-v3");
    }

    @Test
    void manualResolutionMustTellTheCustomerWhatWasAgreed() {
        DroneParcelIncident incident = incident("VERIFIED");
        when(incidentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(incident));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(policyOrder()));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.propose(7L, 1L, new DroneIncidentProposalRequest(
                        "MANUAL_RESOLUTION", false, BigDecimal.ZERO, false, null, "  ")));

        assertEquals("RESOLUTION_NOTE_REQUIRED", error.getCode());
        verify(proposalRepository, never()).save(any());
    }

    /// Thoả thuận riêng không giao lại, không chi qua hệ thống: khách đồng ý là xong — trước đây sự cố
    /// kẹt RESOLUTION_IN_PROGRESS vĩnh viễn (test 2026-10-29, DPI-142).
    @Test
    void acceptingAManualResolutionWithNothingLeftResolvesTheIncident() {
        DroneParcelIncident incident = incident("CLOSED");
        incident.setStatus("AWAITING_CUSTOMER_RESPONSE");
        LockerOrder order = policyOrder();
        order.setUserId(55L);
        order.setStatus("AWAITING_DISPATCH");
        DroneIncidentResolutionProposal proposal = new DroneIncidentResolutionProposal();
        proposal.setProposalVersion(2);
        proposal.setResolutionType("MANUAL_RESOLUTION");
        proposal.setStatus("AWAITING_CUSTOMER_ACCEPTANCE");
        proposal.setCompensationAmount(BigDecimal.ZERO);
        proposal.setResolutionNote("Hoàn tiền mặt tại quầy theo thoả thuận qua điện thoại");
        when(incidentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(incident));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(proposalRepository.findFirstByIncidentIdOrderByProposalVersionDesc(7L)).thenReturn(Optional.of(proposal));

        service.customerRespond(7L, 55L, new DroneIncidentCustomerResponseRequest("ACCEPT", null));

        assertEquals("RESOLVED", incident.getStatus());
        assertNotNull(incident.getResolvedAt());
        assertEquals("CANCELED", order.getStatus());
    }

    /// Phiếu chi bồi thường đã được admin duyệt (payment báo PAID) ⇒ sự cố không còn việc gì ⇒ RESOLVED.
    @Test
    void paidCompensationResolvesTheIncidentAndMarksTheParcelCompensated() {
        DroneParcelIncident incident = incident("VERIFIED");
        incident.setStatus("RESOLUTION_IN_PROGRESS");
        incident.setCompensationStatus("PROCESSING");
        incident.setParcelStatus("LOST");
        when(incidentRepository.findByIncidentCode("DPI-21-1")).thenReturn(Optional.of(incident));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(policyOrder()));

        service.updateCompensation("DPI-21-1", new com.huynqb.laundrylocker.order.dto.IncidentCompensationUpdateRequest(
                "PAID", "RF-CMP-DPI-21-1-1", "Admin đã chuyển khoản"));

        assertEquals("PAID", incident.getCompensationStatus());
        assertEquals("RESOLVED", incident.getStatus());
        assertEquals("COMPENSATED", incident.getParcelStatus());
    }

    /// Đơn giao lại hoàn tất ⇒ phần giao lại của sự cố xong; còn chờ chi bồi thường thì chưa đóng sự cố.
    @Test
    void completedRedeliveryOrderClosesRedeliveryButWaitsForPendingCompensation() {
        DroneParcelIncident incident = incident("CLOSED");
        incident.setStatus("RESOLUTION_IN_PROGRESS");
        incident.setRedeliveryStatus("PREPARING");
        incident.setRedeliveryOrderId(90L);
        incident.setCompensationStatus("PROCESSING");
        when(incidentRepository.findFirstByRedeliveryOrderId(90L)).thenReturn(Optional.of(incident));
        when(incidentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(incident));

        service.onRedeliveryOrderCompleted(90L);

        assertEquals("COMPLETED", incident.getRedeliveryStatus());
        assertEquals("REDELIVERED", incident.getParcelStatus());
        assertEquals("RESOLUTION_IN_PROGRESS", incident.getStatus());

        incident.setRedeliveryStatus("PREPARING");
        incident.setCompensationStatus("NOT_REQUIRED");
        service.onRedeliveryOrderCompleted(90L);
        assertEquals("RESOLVED", incident.getStatus());
    }

    /// Đơn giao lại còn chờ điều phối ⇒ admin không được bấm "đã giao lại thành công" (test 2026-10-29: nút hiện
    /// ngay khi đơn giao lại #144 còn AWAITING_DISPATCH).
    @Test
    void adminCannotCloseRedeliveryWhileTheRedeliveryOrderIsStillOpen() {
        DroneParcelIncident incident = incident("CLOSED");
        incident.setStatus("RESOLUTION_IN_PROGRESS");
        incident.setRedeliveryStatus("PREPARING");
        incident.setRedeliveryOrderId(90L);
        LockerOrder redelivery = new LockerOrder();
        redelivery.setId(90L);
        redelivery.setStatus("AWAITING_DISPATCH");
        when(incidentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(incident));
        when(orderRepository.findById(90L)).thenReturn(Optional.of(redelivery));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.completeRedelivery(7L, 1L, "ADMIN"));
        assertEquals("REDELIVERY_ORDER_NOT_COMPLETED", error.getCode());
        assertEquals("PREPARING", incident.getRedeliveryStatus());

        redelivery.setStatus("COMPLETED");
        when(orderRepository.findById(21L)).thenReturn(Optional.of(policyOrder()));
        service.completeRedelivery(7L, 1L, "ADMIN");
        assertEquals("COMPLETED", incident.getRedeliveryStatus());
    }

    /// Không điều khiển drone từ xa được (DEMO / telemetry cũ): bước quay về không còn là ngõ cụt.
    @Test
    void adminConfirmsTheDroneIsBackWhenReturnNeededManualHandling() {
        DroneParcelIncident incident = incident("SEARCHING");
        incident.setStatus("MANUAL_INTERVENTION_REQUIRED");
        when(incidentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(incident));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(policyOrder()));

        service.confirmDroneReturned(7L, 1L, null);

        assertEquals("COMPLETED", incident.getReturnFlightStatus());
        assertEquals("INVESTIGATING", incident.getStatus());
        BusinessException again = assertThrows(BusinessException.class,
                () -> service.confirmDroneReturned(7L, 1L, null));
        assertEquals("RETURN_FLIGHT_ALREADY_CLOSED", again.getCode());
    }

    /// Gửi phương án ⇒ thư cho người gửi (khách phải vào app đồng ý / yêu cầu xem xét).
    @Test
    void proposalIsEmailedToTheSender() {
        DroneDeliveryMailer mailer = mock(DroneDeliveryMailer.class);
        when(mailer.incidentProposal(any(), any(), any(), any(), any(), any()))
                .thenReturn(new DroneDeliveryMailer.Outcome(true, false));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "mailer", mailer);
        DroneParcelIncident incident = incident("VERIFIED");
        incident.setParcelCondition("MINOR_DAMAGE");
        when(incidentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(incident));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(policyOrder()));
        when(proposalRepository.findFirstByIncidentIdOrderByProposalVersionDesc(7L)).thenReturn(Optional.empty());

        service.propose(7L, 1L, new DroneIncidentProposalRequest("COMPENSATION_ONLY", false,
                new BigDecimal("600.00"), true, null, null));

        verify(mailer).incidentProposal(any(), eq("DPI-21-1"), eq("Bồi thường"), eq("Hư hại nhẹ"),
                eq("600đ (kèm hoàn phí giao)"), isNull());
    }

    /// locker-service sập lúc báo rơi: sự cố vẫn được ghi, nhiệm vụ dừng, thu hồi chờ admin phân công tay.
    @Test
    void dropIsRecordedEvenWhenLockerTicketsCannotBeCreated() {
        LockerOrder order = policyOrder();
        order.setType("DRONE_DELIVERY");
        order.setUserId(55L);
        order.setDeliveryStage("EN_ROUTE");
        DroneMission mission = new DroneMission();
        mission.setId(31L);
        mission.setOrderId(21L);
        mission.setStatus("EN_ROUTE");
        mission.setDroneUnitId(9L);
        mission.setDroneCode("DRONE-09");
        mission.setAssignedByUserId(99L);
        when(incidentRepository.findByIdempotencyKey("drop-key")).thenReturn(Optional.empty());
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));
        when(incidentRepository.findByOrderId(21L)).thenReturn(Optional.empty());
        when(telemetryRegistry.latest("DRONE-09")).thenReturn(Optional.empty());
        when(incidentRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            DroneParcelIncident saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });
        when(lockerDroneClient.createIncidentTickets(any())).thenThrow(new RuntimeException("locker-service down"));

        DroneParcelIncidentResponse response = service.report(21L, 99L, "DRONE_TECHNICIAN", "drop-key",
                new com.huynqb.laundrylocker.order.dto.ReportDroppedParcelRequest(
                        "Rơi kiện", 10.84, 106.81, 5.0, null, null, null));

        assertEquals("INVESTIGATING", response.status());
        assertEquals("AWAITING_ASSIGNMENT", response.recoveryStatus());
        assertEquals("DROP_REPORTED", mission.getStatus());
        assertEquals("DROP_REPORTED", order.getDeliveryStage());
        verify(notificationClient).requestNotification(argThat(n -> n.userId().equals(55L)
                && "DRONE_PARCEL_DROP_REPORTED".equals(n.type()) && "DRONE_ORDER".equals(n.referenceType())
                && n.referenceId().equals(21L)));
    }

    /// Sự cố đã xong (hoặc khách đang xem phương án) ⇒ API không cho gửi phương án nữa, kể cả gọi thẳng.
    @Test
    void proposalIsRejectedOnceTheIncidentIsSettledOrAwaitingTheCustomer() {
        for (String status : List.of("AWAITING_CUSTOMER_RESPONSE", "RESOLUTION_IN_PROGRESS", "RESOLVED")) {
            DroneParcelIncident incident = incident("CLOSED");
            incident.setStatus(status);
            when(incidentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(incident));

            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.propose(7L, 1L, new DroneIncidentProposalRequest("COMPENSATION_ONLY", false,
                            new BigDecimal("600.00"), false, null, null)), status);
            assertEquals("INCIDENT_NOT_PROPOSABLE", error.getCode(), status);
        }
        verify(proposalRepository, never()).save(any());
    }

    /// Khách yêu cầu xem xét ⇒ admin gửi bản mới, bản bị khiếu nại chuyển SUPERSEDED.
    @Test
    void newProposalSupersedesTheDisputedOne() {
        DroneParcelIncident incident = incident("VERIFIED");
        incident.setStatus("DISPUTED");
        DroneIncidentResolutionProposal disputed = new DroneIncidentResolutionProposal();
        disputed.setIncidentId(7L);
        disputed.setProposalVersion(1);
        disputed.setStatus("DISPUTED");
        when(incidentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(incident));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(policyOrder()));
        when(proposalRepository.findFirstByIncidentIdOrderByProposalVersionDesc(7L)).thenReturn(Optional.of(disputed));
        ArgumentCaptor<DroneIncidentResolutionProposal> saved =
                ArgumentCaptor.forClass(DroneIncidentResolutionProposal.class);

        service.propose(7L, 1L, new DroneIncidentProposalRequest("COMPENSATION_ONLY", false,
                new BigDecimal("600.00"), false, null, null));

        verify(proposalRepository, times(2)).save(saved.capture());
        assertEquals("SUPERSEDED", disputed.getStatus());
        assertEquals(2, saved.getAllValues().get(1).getProposalVersion());
        assertEquals("AWAITING_CUSTOMER_RESPONSE", incident.getStatus());
    }

    /// Tiền bồi thường về tài khoản người gửi ⇒ chỉ người gửi quyết định phương án.
    @Test
    void receiverCannotAnswerTheProposalForTheSender() {
        DroneParcelIncident incident = incident("VERIFIED");
        incident.setStatus("AWAITING_CUSTOMER_RESPONSE");
        LockerOrder order = policyOrder();
        order.setUserId(55L);
        order.setReceiverUserId(66L);
        when(incidentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(incident));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.customerRespond(7L, 66L, new DroneIncidentCustomerResponseRequest("ACCEPT", null)));

        assertEquals("INCIDENT_CUSTOMER_FORBIDDEN", error.getCode());
        assertEquals("AWAITING_CUSTOMER_RESPONSE", incident.getStatus());
        verify(proposalRepository, never()).save(any());
    }

    /// Mail 2: admin xác minh kiện đã tìm thấy ⇒ thư + thông báo cho người gửi và người nhận.
    @Test
    void verifyingAFoundParcelTellsTheCustomers() {
        DroneDeliveryMailer mailer = mock(DroneDeliveryMailer.class);
        when(mailer.parcelRecoveryVerified(any(), any(), anyBoolean(), any(), any(), any(), anyBoolean()))
                .thenReturn(new DroneDeliveryMailer.Outcome(true, true));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "mailer", mailer);
        DroneParcelIncident incident = incident("SUBMITTED");
        incident.setStatus("AWAITING_ADMIN_REVIEW");
        incident.setRecoveryOutcome("FOUND");
        incident.setParcelCondition("INTACT");
        LockerOrder order = policyOrder();
        order.setUserId(55L);
        order.setReceiverUserId(66L);
        DroneMission mission = new DroneMission();
        mission.setLaunchingAt(java.time.LocalDateTime.now());
        when(incidentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(incident));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findById(31L)).thenReturn(Optional.of(mission));

        service.verifyRecovery(7L, 1L);

        assertEquals("VERIFIED", incident.getRecoveryStatus());
        verify(mailer).parcelRecoveryVerified(eq(order), eq("DPI-21-1"), eq(true), eq("Còn nguyên vẹn"), isNull(),
                any(), eq(true));
        verify(notificationClient, times(2)).requestNotification(
                argThat(n -> "DRONE_PARCEL_RECOVERY_VERIFIED".equals(n.type())));
    }

    /// Admin xác minh kiện đã tìm thấy ⇒ kiện được điều phối về trạm bay gần nhất còn drone; KTV nhận tên/địa chỉ
    /// trạm để chỉ đường, nơi bàn giao (recoveryLockerId) đổi sang trạm đó để đơn giao lại bay từ đây.
    @Test
    void verifiedFoundParcelIsDispatchedToTheNearestDroneStation() {
        DroneSourceLockerSelector selector = mock(DroneSourceLockerSelector.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "stationSelector", selector);
        DroneParcelIncident incident = incident("SUBMITTED");
        incident.setStatus("AWAITING_ADMIN_REVIEW");
        incident.setRecoveryOutcome("FOUND");
        incident.setRecoveryLockerId(3L);
        incident.setRecoveredLatitude(10.70);
        incident.setRecoveredLongitude(106.70);
        LockerOrder order = policyOrder();
        order.setDestinationLockerId(9L);
        com.huynqb.laundrylocker.order.dto.admin.LockerInfo station = new com.huynqb.laundrylocker.order.dto.admin.LockerInfo(
                5L, 1L, "HCM-05", "Trạm Thủ Đức", "ACTIVE", "1 Võ Văn Ngân", 10.71, 106.71, true, null, "OK", 4, 4);
        when(incidentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(incident));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(selector.recoveryStation(10.70, 106.70, 9L)).thenReturn(Optional.of(
                new DroneSourceLockerSelector.RecoveryStation(station, 1560, 3000, true)));

        DroneParcelIncidentResponse response = service.verifyRecovery(7L, 1L);

        assertEquals(5L, incident.getRecoveryLockerId());
        assertEquals("Trạm Thủ Đức", response.recoveryStationName());
        assertEquals("1 Võ Văn Ngân", response.recoveryStationAddress());
        assertEquals(10.71, response.recoveryStationLatitude());
        assertEquals(1560, response.recoveryStationDistanceM());
        assertEquals(Boolean.TRUE, response.recoveryStationDroneAvailable());
        verify(notificationClient).requestNotification(argThat(n -> n.userId().equals(44L)
                && "DRONE_PARCEL_RECOVERY_STATION".equals(n.type()) && n.message().contains("1 Võ Văn Ngân")));
    }

    /// Kiện không tìm thấy ⇒ không có gì để mang tới trạm.
    @Test
    void notFoundParcelIsNotDispatchedToAStation() {
        DroneSourceLockerSelector selector = mock(DroneSourceLockerSelector.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "stationSelector", selector);
        DroneParcelIncident incident = incident("SUBMITTED");
        incident.setRecoveryOutcome("NOT_FOUND");
        incident.setParcelStatus("LOST");
        when(incidentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(incident));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(policyOrder()));

        service.verifyRecovery(7L, 1L);

        assertNull(incident.getRecoveryStationName());
        verifyNoInteractions(selector);
    }

    /// Webhook "drone không về được" tới muộn không được kéo sự cố đang chờ khách về "cần can thiệp".
    @Test
    void lateReturnFlightFailureKeepsTheIncidentWhereItIs() {
        DroneParcelIncident incident = incident("CLOSED");
        incident.setStatus("AWAITING_CUSTOMER_RESPONSE");
        incident.setReturnFlightStatus("RETURNING");
        when(incidentRepository.findByIncidentCode("DPI-21-1")).thenReturn(Optional.of(incident));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(policyOrder()));

        service.updateReturnFlight("DPI-21-1", new ReturnFlightUpdateRequest("FAILED", "mất kết nối"));

        assertEquals("FAILED", incident.getReturnFlightStatus());
        assertEquals("AWAITING_CUSTOMER_RESPONSE", incident.getStatus());
    }

    @Test
    void returnFlightNeedsPhysicalAcknowledgementsInOrder() {
        DroneParcelIncident incident = incident("ASSIGNED");
        incident.setReturnFlightStatus("RETURN_REQUESTED");
        when(incidentRepository.findByIncidentCode("DPI-21-1")).thenReturn(Optional.of(incident));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(policyOrder()));

        service.updateReturnFlight("DPI-21-1", new ReturnFlightUpdateRequest("COMMAND_ACCEPTED", "autopilot ack"));
        assertEquals("COMMAND_ACCEPTED", incident.getReturnFlightStatus());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateReturnFlight("DPI-21-1", new ReturnFlightUpdateRequest("COMPLETED", null)));
        assertEquals("RETURN_FLIGHT_TRANSITION_INVALID", error.getCode());
        assertEquals("COMMAND_ACCEPTED", incident.getReturnFlightStatus());
    }

    private static DroneParcelIncident incident(String recoveryStatus) {
        DroneParcelIncident value = new DroneParcelIncident();
        value.setId(7L);
        value.setIncidentCode("DPI-21-1");
        value.setOrderId(21L);
        value.setMissionId(31L);
        value.setDroneUnitId(9L);
        value.setDroneCode("DRONE-09");
        value.setReportedByUserId(99L);
        value.setReason("Rơi kiện");
        value.setStatus("INVESTIGATING");
        value.setParcelStatus("RECOVERY_PENDING");
        value.setRecoveryStatus(recoveryStatus);
        value.setRecoveryAssignedToUserId(44L);
        value.setInspectionStatus("ASSIGNED");
        value.setRedeliveryStatus("NOT_REQUIRED");
        value.setCompensationStatus("NOT_REQUIRED");
        value.setReturnFlightStatus("MANUAL_INTERVENTION_REQUIRED");
        value.setGpsSource("MANUAL_DEVICE");
        value.setCameraStatus("UNAVAILABLE");
        return value;
    }

    private static LockerOrder policyOrder() {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setOrderCode("ORD-21");
        order.setIncidentCompensationEnabled(true);
        order.setIncidentCompensationRate(new BigDecimal("60.00"));
        order.setIncidentPolicyVersion("policy-2026-10");
        order.setIncidentFreeRedelivery(true);
        order.setIncidentDisputeAllowed(true);
        order.setParcelDeclaredValue(new BigDecimal("1000.00"));
        return order;
    }
}
