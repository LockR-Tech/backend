package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaymentEventListenerTest {

    @Mock
    private LockerOrderRepository orderRepository;
    @Mock
    private DroneLateRefundScheduler droneLateRefunds;
    @Mock
    private DronePickupService dronePickupService;

    @Mock
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @Mock
    private com.huynqb.laundrylocker.order.client.NotificationClient notificationClient;

    @Mock
    private OrderService orderService;

    @Test
    void paymentCompletedMarksDroneOrderPaidWithoutChangingDispatchStage() {
        OrderPaymentEventListener listener = listener();
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setType("DRONE_DELIVERY");
        order.setPaymentStatus("UNPAID");
        order.setStatus("AWAITING_DISPATCH");
        order.setDeliveryStage("ACCEPTED");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        listener.onPaymentEvent(
                DomainEvent.of(
                        DomainEventNames.PAYMENT_COMPLETED,
                        "payment-service",
                        Map.of("orderId", 21L)));

        assertEquals("PAID", order.getPaymentStatus());
        assertEquals("AWAITING_DISPATCH", order.getStatus());
        assertEquals("ACCEPTED", order.getDeliveryStage());
        verify(orderRepository).save(order);
        verify(droneLateRefunds, never()).schedule(any());
        verify(notificationClient).requestNotification(any());
    }

    @Test
    void moneyArrivingAfterADroneOrderWasCanceledIsSentBackNotKept() {
        OrderPaymentEventListener listener = listener();
        LockerOrder order = droneOrder("CANCELED", "UNPAID");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        listener.onPaymentEvent(completed());

        // Vẫn ghi nhận đã thu để admin thấy nếu hoàn tự động hỏng.
        assertEquals("PAID", order.getPaymentStatus());
        verify(droneLateRefunds).schedule(21L);
        // Khoản này sắp được hoàn: không báo khách "thanh toán thành công".
        verify(notificationClient, never()).requestNotification(any());
    }

    @Test
    void aLatePaymentOnADroneOrderAwaitingRefundDoesNotFlipItBackToPaid() {
        OrderPaymentEventListener listener = listener();
        LockerOrder order = droneOrder("CANCELED", "REFUND_PENDING");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));

        listener.onPaymentEvent(completed());

        assertEquals("REFUND_PENDING", order.getPaymentStatus());
        verify(orderRepository, never()).save(any());
        verify(droneLateRefunds).schedule(21L);
    }

    @Test
    void aSecondLatePaymentOnAnAlreadyRefundedDroneOrderIsAlsoRefunded() {
        OrderPaymentEventListener listener = listener();
        LockerOrder order = droneOrder("CANCELED", "REFUNDED");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));

        listener.onPaymentEvent(completed());

        assertEquals("REFUNDED", order.getPaymentStatus());
        verify(orderRepository, never()).save(any());
        verify(droneLateRefunds).schedule(21L);
    }

    @Test
    void dronePaymentAddsItsOwnAmountAndOnlyMarksPaidOnceTheTotalIsCovered() {
        OrderPaymentEventListener listener = listener();
        LockerOrder order = droneOrder("AWAITING_DISPATCH", "UNPAID");
        // Đã trả 15.000 đ phí ban đầu, sau đó cân lệch cộng thêm 3.000 đ.
        order.setTotalPrice(new java.math.BigDecimal("18000"));
        order.setPaidAmount(new java.math.BigDecimal("15000"));
        order.setLastPaymentId(501L);
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Sự kiện lặp lại của lần trả đầu (cổng thanh toán gửi lại callback) không được lật đơn về PAID.
        listener.onPaymentEvent(paid(501L, "15000"));
        assertEquals("UNPAID", order.getPaymentStatus());
        assertEquals(0, new java.math.BigDecimal("15000").compareTo(order.getPaidAmount()));

        // Trả thiếu vẫn còn nợ.
        listener.onPaymentEvent(paid(502L, "1000"));
        assertEquals("UNPAID", order.getPaymentStatus());
        assertEquals(0, new java.math.BigDecimal("16000").compareTo(order.getPaidAmount()));

        listener.onPaymentEvent(paid(503L, "2000"));
        assertEquals("PAID", order.getPaymentStatus());
        assertEquals(0, new java.math.BigDecimal("18000").compareTo(order.getPaidAmount()));
        assertEquals(503L, order.getLastPaymentId());
        verify(notificationClient).requestNotification(any());
    }

    /// Khách chuyển thiếu cho đơn gửi hàng: khoản đó được cộng vào số đã trả nhưng đơn chưa PAID,
    /// chuyển bù đủ thì mới PAID.
    @Test
    void underpaidLockerOrderStaysUnpaidUntilTheRestArrives() {
        OrderPaymentEventListener listener = listener();
        LockerOrder order = droneOrder("INITIALIZED", "UNPAID");
        order.setType("SEND");
        order.setTotalPrice(new java.math.BigDecimal("15000"));
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        listener.onPaymentEvent(paid(601L, "1000"));
        assertEquals("UNPAID", order.getPaymentStatus());
        assertEquals(0, new java.math.BigDecimal("1000").compareTo(order.getPaidAmount()));

        listener.onPaymentEvent(paid(602L, "14000"));
        assertEquals("PAID", order.getPaymentStatus());
        assertEquals(0, new java.math.BigDecimal("15000").compareTo(order.getPaidAmount()));
    }

    /// Đơn thuê ô đã huỷ (vd khách bấm huỷ ở kiosk lúc chuyển khoản còn chờ) mà tiền mới về ⇒ hoàn lại.
    @Test
    void moneyArrivingAfterALockerOrderWasCanceledIsSentBack() {
        OrderPaymentEventListener listener = listener();
        LockerOrder order = droneOrder("CANCELED", "UNPAID");
        order.setType("RENTAL");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        listener.onPaymentEvent(paid(603L, "20000"));

        verify(droneLateRefunds).schedule(21L);
        verify(notificationClient, never()).requestNotification(any());
    }

    @Test
    void paidSendOrderTellsTheReceiverOnce() {
        OrderPaymentEventListener listener = listener();
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setType("SEND");
        order.setStatus("INITIALIZED");
        order.setPaymentStatus("UNPAID");
        order.setTotalPrice(new java.math.BigDecimal("15000"));
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        listener.onPaymentEvent(paid(5L, "15000"));
        listener.onPaymentEvent(paid(5L, "15000"));

        assertEquals("PAID", order.getPaymentStatus());
        verify(orderService).notifyReceiverOrderPaid(order);
    }

    @Test
    void partialPaymentDoesNotTellTheReceiverYet() {
        OrderPaymentEventListener listener = listener();
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setType("SEND");
        order.setStatus("INITIALIZED");
        order.setPaymentStatus("UNPAID");
        order.setTotalPrice(new java.math.BigDecimal("15000"));
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        listener.onPaymentEvent(paid(5L, "5000"));

        verify(orderService, never()).notifyReceiverOrderPaid(any());
    }

    private static DomainEvent paid(long paymentId, String amount) {
        return DomainEvent.of(
                DomainEventNames.PAYMENT_COMPLETED,
                "payment-service",
                Map.of("orderId", 21L, "paymentId", paymentId, "amount", new java.math.BigDecimal(amount)));
    }

    private OrderPaymentEventListener listener() {
        return new OrderPaymentEventListener(
                orderRepository, droneLateRefunds,
                new PaidOrderEffects(rabbitTemplate, notificationClient, dronePickupService, orderRepository),
                orderService);
    }

    private static LockerOrder droneOrder(String status, String paymentStatus) {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setType("DRONE_DELIVERY");
        order.setStatus(status);
        order.setPaymentStatus(paymentStatus);
        return order;
    }

    private static DomainEvent completed() {
        return DomainEvent.of(DomainEventNames.PAYMENT_COMPLETED, "payment-service", Map.of("orderId", 21L));
    }
}
