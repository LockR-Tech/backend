package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.dto.DroneDeliveryOrderResponse;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DroneDeliveryQueryServiceTest {

    @Mock
    private LockerOrderRepository orderRepository;
    @Mock
    private DroneMissionRepository missionRepository;

    @Test
    void returnsOrderAndMissionAsOneCustomerReadModel() {
        DroneDeliveryQueryService service = new DroneDeliveryQueryService(orderRepository, missionRepository);
        LockerOrder order = order();
        DroneMission mission = new DroneMission();
        mission.setId(301L);
        mission.setOrderId(21L);
        mission.setStatus("EN_ROUTE");
        mission.setDroneUnitId(9L);
        mission.setDroneCode("DRONE-09");
        mission.setSourceLockerId(1L);
        mission.setDestinationLockerId(5L);
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));

        DroneDeliveryOrderResponse response = service.get(21L, 44L);

        assertEquals("EN_ROUTE", response.deliveryStage());
        assertEquals(301L, response.missionId());
        assertEquals("DRONE-09", response.droneCode());
        assertEquals(1L, response.sourceLockerId());
        assertEquals(5L, response.destinationLockerId());
        assertEquals(6, response.etaMinutes());
    }

    @Test
    void rejectsReadByAnotherCustomer() {
        DroneDeliveryQueryService service = new DroneDeliveryQueryService(orderRepository, missionRepository);
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order()));

        BusinessException error = assertThrows(BusinessException.class, () -> service.get(21L, 99L));

        assertEquals("ORDER_FORBIDDEN", error.getCode());
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
