package com.huynqb.laundrylocker.payment.controller;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.payment.dto.CreatePaymentRequest;
import com.huynqb.laundrylocker.payment.dto.RefundRequest;
import com.huynqb.laundrylocker.payment.service.PaymentService;
import com.huynqb.laundrylocker.payment.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/// Đường tạo giao dịch / hoàn tiền với id tuỳ ý trong body chỉ dành cho ADMIN; khách dùng checkout / refund-request.
@ExtendWith(MockitoExtension.class)
class PaymentControllerAccessTest {

    @Mock private PaymentService paymentService;
    @Mock private WalletService walletService;
    @InjectMocks private PaymentController controller;

    @Test
    void directPaymentCreationIsAdminOnly() {
        CreatePaymentRequest request = new CreatePaymentRequest(
                55L, 44L, BigDecimal.ONE, "CASH", null, null, null, null);

        BusinessException error = assertThrows(BusinessException.class, () -> controller.create(request, "CUSTOMER"));
        assertEquals("PAYMENT_CREATE_FORBIDDEN", error.getCode());
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        assertThrows(BusinessException.class, () -> controller.createLegacy(request, null));
        verify(paymentService, never()).create(any());

        controller.create(request, "ADMIN");
        verify(paymentService).create(request);
    }

    @Test
    void refundForArbitraryPaymentIsAdminOnly() {
        RefundRequest request = new RefundRequest(BigDecimal.TEN, "test");

        assertEquals("REFUND_CREATE_FORBIDDEN", assertThrows(BusinessException.class,
                () -> controller.refund(9L, request, 44L, "CUSTOMER")).getCode());
        verify(paymentService, never()).refund(any(), any(), any());
    }
}
