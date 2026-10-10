package com.huynqb.laundrylocker.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.DroneIncidentCustomerResponseRequest;
import com.huynqb.laundrylocker.order.dto.DroneIncidentProposalRequest;
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
    @Mock UserClient userClient;
    @Mock NotificationClient notificationClient;
    @Mock DroneReturnCommandGateway returnCommandGateway;
    @Mock IncidentCompensationGateway compensationGateway;
    @Mock IncidentRedeliveryGateway redeliveryGateway;
    @Mock CloudinaryMediaStorage mediaStorage;

    private DroneParcelIncidentService service;

    @BeforeEach
    void setUp() {
        service = new DroneParcelIncidentService(
                incidentRepository, evidenceRepository, timelineRepository, proposalRepository,
                orderRepository, missionRepository, orderHistoryRepository, telemetryRegistry,
                lockerDroneClient, userClient, notificationClient, returnCommandGateway,
                compensationGateway, redeliveryGateway, mediaStorage, new ObjectMapper());
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
                                new BigDecimal("601.00"), false, null)));
        assertEquals("COMPENSATION_OVERRIDE_REASON_REQUIRED", error.getCode());

        service.propose(7L, 1L,
                new DroneIncidentProposalRequest("COMPENSATION_ONLY", false,
                        new BigDecimal("600.00"), false, null));

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
        assertFalse(camera.containsKey("streamUrl"));
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
