package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.model.DroneParcelIncident;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneParcelIncidentRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderIncidentRedeliveryGatewayTest {
    @Mock LockerOrderRepository orderRepository;
    @Mock OrderStatusHistoryRepository historyRepository;
    @Mock DroneParcelIncidentRepository incidentRepository;
    @InjectMocks OrderIncidentRedeliveryGateway gateway;

    @Test
    void repeatedAttemptReturnsTheExistingRedeliveryOrder() {
        when(orderRepository.findById(21L)).thenReturn(Optional.of(oldOrder()));
        DroneParcelIncident incident = new DroneParcelIncident();
        incident.setId(7L);
        incident.setIncidentCode("DPI-21-1");
        incident.setRedeliveryOrderId(55L);
        when(incidentRepository.findById(7L)).thenReturn(Optional.of(incident));

        IncidentRedeliveryGateway.Submission result = gateway.createAttempt(7L, 21L, 3L, "key");

        assertTrue(result.accepted());
        assertEquals(55L, result.deliveryOrderId());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void failureHalfwayIsNotSwallowedSoTheAcceptanceRollsBack() {
        when(orderRepository.findById(21L)).thenReturn(Optional.of(oldOrder()));
        when(incidentRepository.findById(7L)).thenReturn(Optional.empty());
        when(orderRepository.save(any())).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class, () -> gateway.createAttempt(7L, 21L, 3L, "key"));
    }

    private static LockerOrder oldOrder() {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setOrderCode("ORD-21");
        order.setUserId(3L);
        order.setSourceLockerId(1L);
        order.setDestinationLockerId(2L);
        return order;
    }
}
