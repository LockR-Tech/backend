package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.dto.DroneDeliveryOrderResponse;
import com.huynqb.laundrylocker.order.dto.admin.BoxInfo;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.dto.admin.OrderPaymentSummary;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.service.AdminReferenceResolver.Lookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DroneDeliveryQueryServiceTest {

    @Mock
    private LockerOrderRepository orderRepository;
    @Mock
    private DroneMissionRepository missionRepository;
    @Mock
    private OrderStatusHistoryRepository historyRepository;
    @Mock
    private AdminReferenceResolver references;

    private DroneDeliveryQueryService service;

    @BeforeEach
    void setUp() {
        service = new DroneDeliveryQueryService(orderRepository, missionRepository, historyRepository, references);
        lenient().when(references.lockers(any())).thenReturn(Lookup.of(Map.of()));
        lenient().when(references.boxes(any())).thenReturn(Lookup.of(Map.of()));
        lenient().when(references.users(any())).thenReturn(Lookup.of(Map.of()));
    }

    @Test
    void returnsOrderAndMissionAsOneCustomerReadModel() {
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order()));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission(77L)));

        DroneDeliveryOrderResponse response = service.get(21L, 44L);

        assertEquals("EN_ROUTE", response.deliveryStage());
        assertEquals(301L, response.missionId());
        assertEquals("DRONE-09", response.droneCode());
        assertEquals(1L, response.sourceLockerId());
        assertEquals(5L, response.destinationLockerId());
        assertEquals(6, response.etaMinutes());
    }

    @Test
    void resolvesLockerBoxAndPeopleNamesForDisplay() {
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order()));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission(77L)));
        OrderStatusHistory accepted = new OrderStatusHistory();
        accepted.setOrderId(21L);
        accepted.setOldStatus("AWAITING_DISPATCH");
        accepted.setNewStatus("ACCEPTED");
        accepted.setChangedByUserId(77L);
        when(historyRepository.findByOrderIdOrderByCreatedAtDescIdDesc(21L)).thenReturn(List.of(accepted));
        when(references.lockers(any())).thenReturn(Lookup.of(Map.of(
                1L, new LockerInfo(1L, 1L, "LK-A", "Locker A", "ACTIVE", "1 Le Loi", 10.77, 106.70, true, null, 8, 4),
                5L, new LockerInfo(5L, 1L, "LK-B", "Locker B", "ACTIVE", "9 Nguyen Hue", 10.78, 106.71, true, null, 8, 4))));
        when(references.boxes(any())).thenReturn(Lookup.of(Map.of(
                9001L, new BoxInfo(9001L, 5L, 7, "MEDIUM", "DRONE", "RESERVED", true))));
        when(references.users(any())).thenReturn(Lookup.of(Map.of(
                44L, new UserSummary(44L, "a@lockr.vn", "0900000044", "Khach A", "ACTIVE"),
                77L, new UserSummary(77L, "t@lockr.vn", "0900000077", "Dieu Phoi Vien", "ACTIVE"))));

        DroneDeliveryOrderResponse response = service.get(21L, 44L);

        assertEquals("Locker A", response.sourceLocker().name());
        assertEquals(10.78, response.destinationLocker().latitude());
        assertEquals(7, response.reservedBoxNumber());
        assertEquals("Khach A", response.customerName());
        assertEquals("Dieu Phoi Vien", response.assignedByName());
        assertEquals("Dieu Phoi Vien", response.journeyEvents().getFirst().actorName());
        // Chưa xác nhận nạp hàng thì checklist chưa có giá trị, không phải "không đạt".
        assertNull(response.parcelMatched());
    }

    @Test
    void paidOrderShowsTheTransactionThatPaidIt() {
        LockerOrder order = order();
        order.setPaymentStatus("PAID");
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission(77L)));
        when(references.paymentSummaries(any())).thenReturn(Lookup.of(Map.of(21L, new OrderPaymentSummary(
                21L, 1, 90L, "VNPAY", "COMPLETED", BigDecimal.valueOf(30000), null,
                BigDecimal.valueOf(30000), "VNPAY", null, BigDecimal.ZERO, "PAY-21-ABC", "14523311"))));

        DroneDeliveryOrderResponse response = service.get(21L, 44L);

        assertEquals("VNPAY", response.paymentMethod());
        assertEquals("PAY-21-ABC", response.paymentReference());
        assertEquals("14523311", response.paymentTransactionId());
    }

    @Test
    void unpaidOrderDoesNotAskPaymentServiceForATransaction() {
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order()));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission(77L)));

        DroneDeliveryOrderResponse response = service.get(21L, 44L);

        assertNull(response.paymentReference());
        verify(references).paymentSummaries(Set.of());
    }

    @Test
    void rejectsReadByAnotherCustomer() {
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order()));

        BusinessException error = assertThrows(BusinessException.class, () -> service.get(21L, 99L));

        assertEquals("ORDER_FORBIDDEN", error.getCode());
    }

    @Test
    void technicianSeesQueueAndOwnMissionsOnly() {
        LockerOrder waiting = order();
        waiting.setId(22L);
        waiting.setDeliveryStage("AWAITING_DISPATCH");
        LockerOrder mine = order();
        LockerOrder others = order();
        others.setId(23L);
        when(orderRepository.findByTypeAndStatusNotInOrderByUpdatedAtDesc(any(), any()))
                .thenReturn(List.of(waiting, mine, others));
        when(missionRepository.findByOrderId(22L)).thenReturn(Optional.empty());
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission(77L)));
        when(missionRepository.findByOrderId(23L)).thenReturn(Optional.of(mission(88L)));

        List<DroneDeliveryOrderResponse> visible = service.operations(null, 77L, false);

        assertEquals(List.of(22L, 21L), visible.stream().map(DroneDeliveryOrderResponse::orderId).toList());
    }

    private DroneMission mission(Long assignedBy) {
        DroneMission mission = new DroneMission();
        mission.setId(301L);
        mission.setOrderId(21L);
        mission.setStatus("EN_ROUTE");
        mission.setDroneUnitId(9L);
        mission.setDroneCode("DRONE-09");
        mission.setSourceLockerId(1L);
        mission.setDestinationLockerId(5L);
        mission.setAssignedByUserId(assignedBy);
        return mission;
    }

    private LockerOrder order() {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setOrderCode("ORD-21");
        order.setUserId(44L);
        order.setReceiverUserId(44L);
        order.setDestinationLockerId(5L);
        order.setReservedBoxId(9001L);
        order.setType("DRONE_DELIVERY");
        order.setFulfillmentMode("DEMO");
        order.setStatus("AWAITING_DISPATCH");
        order.setDeliveryStage("EN_ROUTE");
        order.setPaymentStatus("UNPAID");
        return order;
    }
}
