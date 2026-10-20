package com.huynqb.laundrylocker.order.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderSchedulerRefundTest {

    /// Đơn đã trả tiền bị tự huỷ (không bỏ hàng kịp hạn) ⇒ tạo yêu cầu hoàn cho từng đơn; một đơn lỗi
    /// không chặn các đơn sau.
    @Test
    void paidOrdersCanceledBySweepAreRefundedOneByOne() {
        DroneRefundService refunds = mock(DroneRefundService.class);
        when(refunds.refundCanceledOrder(7L, null)).thenThrow(new IllegalStateException("payment down"));

        OrderScheduler.refundPaidCanceled(
                Map.of("canceledOrders", 3, "paidCanceledOrderIds", List.of(7L, 8L)), refunds);

        verify(refunds).refundCanceledOrder(7L, null);
        verify(refunds).refundCanceledOrder(8L, null);
    }

    @Test
    void sweepWithoutPaidOrdersRefundsNothing() {
        DroneRefundService refunds = mock(DroneRefundService.class);

        OrderScheduler.refundPaidCanceled(Map.of("canceledOrders", 2, "paidCanceledOrderIds", List.of()), refunds);
        OrderScheduler.refundPaidCanceled(null, refunds);

        verify(refunds, never()).refundCanceledOrder(any(), any());
    }
}
