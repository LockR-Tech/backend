package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.dto.OrderBleAccessResponse;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Mở ô theo đơn qua Bluetooth (F3-G11): ai được xin mã, lúc nào, để làm gì — cùng luật mở bằng PIN/QR.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BleOrderAccessServiceTest {

    static final long OWNER = 10L, RECEIVER = 55L, DELEGATE = 66L, STRANGER = 99L;

    @Mock LockerOrderRepository orderRepository;
    @Mock OrderService orderService;
    @Mock ReceiverOrderQueryService receiverOrders;
    BleOrderAccessService service;

    @BeforeEach
    void setUp() {
        service = new BleOrderAccessService(orderRepository, orderService, receiverOrders);
    }

    @Test
    void ownerDropsIntoTheSendBoxOfAPaidNewOrder() {
        LockerOrder order = order("SEND", "INITIALIZED");

        OrderBleAccessResponse r = service.check(21L, OWNER);

        assertEquals("DROP", r.action());
        assertNull(r.denyCode());
        assertEquals(7L, r.lockerId());
        assertEquals(70L, r.boxId());
        assertEquals(OWNER, r.userId());
        verify(orderService).assertPaidBeforeAccess(order);
    }

    @Test
    void onlyTheOwnerMayDrop() {
        order("SEND", "INITIALIZED");
        assertDenied(service.check(21L, RECEIVER), "BLE_ORDER_FORBIDDEN", 403);
    }

    @Test
    void unpaidOrderIsRefusedWithTheSameCodeAsThePin() {
        LockerOrder order = order("SEND", "INITIALIZED");
        doThrow(new BusinessException("ORDER_UNPAID", "Vui lòng thanh toán"))
                .when(orderService).assertPaidBeforeAccess(order);

        assertDenied(service.check(21L, OWNER), "ORDER_UNPAID", 400);
    }

    @Test
    void renterOpensARentalWhileStoring() {
        order("RENTAL", "STORING");
        assertEquals("RENTAL_ACCESS", service.check(21L, OWNER).action());
        assertDenied(service.check(21L, RECEIVER), "BLE_ORDER_FORBIDDEN", 403);
    }

    @Test
    void expiredRentalKeepsItsBlockReason() {
        LockerOrder order = order("RENTAL", "STORING");
        when(orderService.accessBlockReason(order)).thenReturn("RENTAL_EXPIRED");
        assertDenied(service.check(21L, OWNER), "RENTAL_EXPIRED", 409);
    }

    @Test
    void receiverHoldingTheCodePicksUpFromTheReceiveBox() {
        LockerOrder order = order("SEND", "STORING");
        order.setReceiveBoxId(71L);
        when(receiverOrders.holdsPickupCode(order, RECEIVER)).thenReturn(true);

        OrderBleAccessResponse r = service.check(21L, RECEIVER);

        assertEquals("PICKUP", r.action());
        assertEquals(71L, r.boxId());
    }

    @Test
    void onDelegatedOrderOnlyTheDelegatePicksUp() {
        LockerOrder order = order("SEND", "STORING");
        order.setReceiverUserId(RECEIVER);
        order.setDelegatePhone("0907654321");
        when(receiverOrders.holdsPickupCode(order, RECEIVER)).thenReturn(false);
        when(receiverOrders.holdsPickupCode(order, DELEGATE)).thenReturn(true);

        assertDenied(service.check(21L, RECEIVER), "BLE_ORDER_FORBIDDEN", 403);
        assertEquals("PICKUP", service.check(21L, DELEGATE).action());
    }

    @Test
    void senderPicksUpOnlyWhenSendingToThemselves() {
        LockerOrder order = order("SEND", "STORING");
        assertDenied(service.check(21L, OWNER), "BLE_ORDER_FORBIDDEN", 403);

        order.setReceiverUserId(OWNER);
        assertEquals("PICKUP", service.check(21L, OWNER).action());

        order.setDelegatePhone("0907654321");
        assertDenied(service.check(21L, OWNER), "BLE_ORDER_FORBIDDEN", 403);
    }

    @Test
    void returnedParcelGoesBackToTheSender() {
        order("SEND", "RETURNED");
        assertEquals("PICKUP", service.check(21L, OWNER).action());
        assertDenied(service.check(21L, RECEIVER), "BLE_ORDER_FORBIDDEN", 403);
    }

    @Test
    void droneParcelOpensOnlyOnceInTheCell() {
        LockerOrder order = order("DRONE_DELIVERY", "STORING");
        order.setDeliveryStage("IN_FLIGHT");
        when(receiverOrders.holdsPickupCode(order, RECEIVER)).thenReturn(true);
        assertDenied(service.check(21L, RECEIVER), "BLE_ORDER_NOT_OPENABLE", 409);

        order.setDeliveryStage("READY_FOR_PICKUP");
        assertEquals("PICKUP", service.check(21L, RECEIVER).action());
    }

    @Test
    void finishedOrderIsNotOpenableForParticipantsAndForbiddenForOthers() {
        LockerOrder order = order("SEND", "COMPLETED");
        assertDenied(service.check(21L, OWNER), "BLE_ORDER_NOT_OPENABLE", 409);
        when(receiverOrders.isReceiverOrDelegate(order, STRANGER)).thenReturn(false);
        assertDenied(service.check(21L, STRANGER), "BLE_ORDER_FORBIDDEN", 403);
        verify(orderService, never()).assertPaidBeforeAccess(any());
    }

    @Test
    void strangerIsForbiddenAndMissingOrderIsNotFound() {
        order("SEND", "STORING");
        assertDenied(service.check(21L, STRANGER), "BLE_ORDER_FORBIDDEN", 403);
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());
        assertDenied(service.check(404L, OWNER), "ORDER_NOT_FOUND", 404);
    }

    @Test
    void userServiceOutageIsPassedOnInsteadOfForbidden() {
        LockerOrder order = order("SEND", "STORING");
        when(receiverOrders.holdsPickupCode(eq(order), eq(RECEIVER))).thenThrow(new BusinessException(
                "USER_LOOKUP_UNAVAILABLE", "Không tra được thông tin tài khoản", HttpStatus.SERVICE_UNAVAILABLE));
        assertDenied(service.check(21L, RECEIVER), "USER_LOOKUP_UNAVAILABLE", 503);
    }

    private LockerOrder order(String type, String status) {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setUserId(OWNER);
        order.setType(type);
        order.setStatus(status);
        order.setLockerId(7L);
        order.setSendBoxId(70L);
        order.setReceiverPhone("0901234567");
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        return order;
    }

    private static void assertDenied(OrderBleAccessResponse r, String code, int status) {
        assertEquals(code, r.denyCode());
        assertEquals(status, r.denyStatus());
        assertNull(r.action());
    }
}
