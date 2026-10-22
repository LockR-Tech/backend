package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/// SEC-05: chi tiết/trạng thái đơn (có PIN, QR) chỉ chủ đơn hoặc ADMIN; lịch sử, đánh giá, khiếu
/// nại thêm người nhận/người lấy hộ. Người khác nhận 404 như đơn không tồn tại.
@ExtendWith(MockitoExtension.class)
class OrderAccessGuardTest {

    @Mock private LockerOrderRepository orderRepository;
    @Mock private ReceiverOrderQueryService receiverOrders;

    private OrderAccessGuard guard;
    private LockerOrder order;

    @BeforeEach
    void setUp() {
        guard = new OrderAccessGuard(orderRepository, receiverOrders);
        order = new LockerOrder();
        order.setId(7L);
        order.setUserId(44L);
    }

    @Test
    void ownerReadsOrderWithPin() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertDoesNotThrow(() -> guard.requireOwner(7L, 44L, "CUSTOMER"));
    }

    @Test
    void otherCustomerGetsNotFoundForOrderWithPin() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThrows(NotFoundException.class, () -> guard.requireOwner(7L, 99L, "CUSTOMER"));
        // Người nhận cũng không đọc đơn qua đường này: PIN của họ nằm ở /api/orders/incoming/{id}.
        verify(receiverOrders, never()).isReceiverOrDelegate(any(), any());
    }

    @Test
    void technicianIsNotAnOwner() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThrows(NotFoundException.class, () -> guard.requireOwner(7L, 99L, "LOCKER_TECHNICIAN"));
    }

    @Test
    void adminReadsAnyOrderWithoutLookup() {
        assertDoesNotThrow(() -> guard.requireOwner(7L, 1L, "ADMIN"));
        assertDoesNotThrow(() -> guard.requireParticipant(7L, 1L, "CUSTOMER,ADMIN"));

        verifyNoInteractions(orderRepository, receiverOrders);
    }

    @Test
    void missingOrderIsNotFound() {
        when(orderRepository.findById(8L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> guard.requireOwner(8L, 44L, "CUSTOMER"));
    }

    @Test
    void receiverOrDelegateReadsHistory() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(receiverOrders.isReceiverOrDelegate(order, 55L)).thenReturn(true);

        assertDoesNotThrow(() -> guard.requireParticipant(7L, 55L, "CUSTOMER"));
    }

    @Test
    void ownerReadsHistoryWithoutReceiverLookup() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertDoesNotThrow(() -> guard.requireParticipant(7L, 44L, "CUSTOMER"));
        verify(receiverOrders, never()).isReceiverOrDelegate(any(), any());
    }

    @Test
    void strangerGetsNotFoundForHistory() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(receiverOrders.isReceiverOrDelegate(order, 99L)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> guard.requireParticipant(7L, 99L, "CUSTOMER"));
    }
}
