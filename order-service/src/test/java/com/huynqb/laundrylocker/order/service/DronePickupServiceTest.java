package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.DronePickup;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.DronePickupOfferRepository;
import com.huynqb.laundrylocker.order.repository.DronePickupRepository;
import com.huynqb.laundrylocker.order.repository.DroneTechnicianPresenceRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DronePickupServiceTest {

    private static final Long TECHNICIAN_ID = 77L;

    @Mock private LockerOrderRepository orderRepository;
    @Mock private DronePickupRepository pickupRepository;
    @Mock private DronePickupOfferRepository offerRepository;
    @Mock private DroneTechnicianPresenceRepository presenceRepository;
    @Mock private DroneMissionRepository missionRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private NotificationClient notificationClient;
    @Mock private DroneDeliveryFailureService failureService;
    @Mock private UserClient userClient;
    @Mock private OrderRules rules;
    @Mock private CloudinaryMediaStorage mediaStorage;

    private DronePickupService service;

    @BeforeEach
    void setUp() {
        service = new DronePickupService(
                orderRepository,
                pickupRepository,
                offerRepository,
                presenceRepository,
                missionRepository,
                historyRepository,
                notificationClient,
                failureService,
                userClient,
                rules,
                mediaStorage);
    }

    @Test
    void activeAcceptedPickupKeepsTechnicianBusy() {
        DronePickup pickup = pickup(10L, DronePickup.ACCEPTED);
        when(pickupRepository.findByTechnicianIdAndStatusInOrderByCreatedAtDesc(
                eq(TECHNICIAN_ID), anyCollection())).thenReturn(List.of(pickup));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order(10L, "AWAITING_DISPATCH", "PICKUP_ASSIGNED")));

        assertTrue(service.busy(TECHNICIAN_ID));
    }

    @Test
    void stalePickupOfCanceledOrderDoesNotKeepTechnicianBusy() {
        DronePickup pickup = pickup(10L, DronePickup.ACCEPTED);
        when(pickupRepository.findByTechnicianIdAndStatusInOrderByCreatedAtDesc(
                eq(TECHNICIAN_ID), anyCollection())).thenAnswer(invocation -> {
                    Collection<String> statuses = invocation.getArgument(1);
                    return statuses.contains(DronePickup.ACCEPTED) ? List.of(pickup) : List.of();
                });
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order(10L, "CANCELED", "CANCELED")));
        when(missionRepository.findByAssignedByUserIdAndStatusIn(eq(TECHNICIAN_ID), anyCollection()))
                .thenReturn(List.of());

        assertFalse(service.busy(TECHNICIAN_ID));
    }

    @Test
    void offerPendingFallsBackToOnDutyTechnicianWhenLocationIsStale() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        when(rules.droneTechnicianLocationMaxAgeMinutes()).thenReturn(10);
        when(rules.dronePickupOfferSeconds()).thenReturn(180);

        LockerOrder order = order(30L, "AWAITING_DISPATCH", "AWAITING_DISPATCH");
        order.setType("DRONE_DELIVERY");
        order.setPickupLatitude(10.7769);
        order.setPickupLongitude(106.7009);
        order.setOrderCode("ORD-30");
        order.setPickupAddress("123 Test St");

        DronePickup waiting = pickup(30L, DronePickup.SEARCHING);
        waiting.setId(99L);
        when(pickupRepository.findByStatusInOrderByCreatedAtAsc(List.of(DronePickup.SEARCHING)))
                .thenReturn(List.of(waiting));
        when(orderRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(order));
        when(pickupRepository.findByOrderIdForUpdate(30L)).thenReturn(Optional.of(waiting));
        when(offerRepository.findByPickupId(99L)).thenReturn(List.of());

        // Location was updated 20 minutes ago (> 10 mins)
        com.huynqb.laundrylocker.order.model.DroneTechnicianPresence presence =
                new com.huynqb.laundrylocker.order.model.DroneTechnicianPresence();
        presence.setUserId(TECHNICIAN_ID);
        presence.setOnDuty(true);
        presence.setLatitude(10.7770);
        presence.setLongitude(106.7010);
        presence.setLocationAt(now.minusMinutes(20));

        when(presenceRepository.findByOnDutyTrue()).thenReturn(List.of(presence));
        when(presenceRepository.findByUserIdForUpdate(TECHNICIAN_ID)).thenReturn(Optional.of(presence));
        when(pickupRepository.findByTechnicianIdAndStatusInOrderByCreatedAtDesc(eq(TECHNICIAN_ID), anyCollection()))
                .thenReturn(List.of());
        when(missionRepository.findByAssignedByUserIdAndStatusIn(eq(TECHNICIAN_ID), anyCollection()))
                .thenReturn(List.of());

        int offered = service.offerPending(now);

        org.junit.jupiter.api.Assertions.assertEquals(1, offered);
        org.junit.jupiter.api.Assertions.assertEquals(DronePickup.OFFERED, waiting.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(TECHNICIAN_ID, waiting.getTechnicianId());
    }

    @Test
    void staleActiveMissionOfCompletedOrderDoesNotKeepTechnicianBusy() {
        when(pickupRepository.findByTechnicianIdAndStatusInOrderByCreatedAtDesc(
                eq(TECHNICIAN_ID), anyCollection())).thenReturn(List.of());
        DroneMission mission = new DroneMission();
        mission.setOrderId(20L);
        mission.setAssignedByUserId(TECHNICIAN_ID);
        mission.setStatus("EN_ROUTE");
        when(missionRepository.findByAssignedByUserIdAndStatusIn(eq(TECHNICIAN_ID), anyCollection()))
                .thenReturn(List.of(mission));
        when(orderRepository.findById(20L)).thenReturn(Optional.of(order(20L, "COMPLETED", "READY_FOR_PICKUP")));

        assertFalse(service.busy(TECHNICIAN_ID));
    }

    private static DronePickup pickup(Long orderId, String status) {
        DronePickup pickup = new DronePickup();
        pickup.setOrderId(orderId);
        pickup.setTechnicianId(TECHNICIAN_ID);
        pickup.setStatus(status);
        return pickup;
    }

    private static LockerOrder order(Long id, String status, String stage) {
        LockerOrder order = new LockerOrder();
        order.setId(id);
        order.setStatus(status);
        order.setDeliveryStage(stage);
        return order;
    }
}
