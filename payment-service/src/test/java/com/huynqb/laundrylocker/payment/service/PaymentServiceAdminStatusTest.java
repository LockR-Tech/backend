package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.payment.client.OrderClient;
import com.huynqb.laundrylocker.payment.dto.UpdatePaymentStatusRequest;
import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import com.huynqb.laundrylocker.payment.repository.PaymentRepository;
import com.huynqb.laundrylocker.payment.repository.RefundRepository;
import com.huynqb.laundrylocker.payment.settings.TestPaymentRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/// Admin đổi tay trạng thái giao dịch: chặn nạp ví và giao dịch đã chốt sổ.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceAdminStatusTest {

    @Mock private PaymentRepository repository;
    @Mock private RefundRepository refundRepository;
    @Mock private com.huynqb.laundrylocker.payment.repository.UserBankAccountRepository userBankAccountRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private Environment environment;
    @Mock private WalletService walletService;
    @Mock private OrderClient orderClient;
    @Mock private com.huynqb.laundrylocker.payment.client.NotificationClient notificationClient;
    @Mock private MomoService momoService;
    @Mock private SepayService sepayService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                repository, refundRepository, userBankAccountRepository, rabbitTemplate, environment,
                walletService, orderClient, notificationClient, momoService, sepayService,
                TestPaymentRules.defaults());
        when(repository.save(any(PaymentRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void topupStatusCannotBeChangedByHand() {
        payment(1L, "SEPAY_TOPUP", "PENDING");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.updateStatus(1L, new UpdatePaymentStatusRequest("COMPLETED")));

        assertEquals("PAYMENT_STATUS_LOCKED", ex.getCode());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(repository, never()).save(any());
        verifyNoInteractions(walletService, rabbitTemplate);
    }

    @Test
    void completedOrRefundedPaymentIsLocked() {
        payment(2L, "CASH", "COMPLETED");
        payment(3L, "SEPAY", "REFUNDED");

        BusinessException completed = assertThrows(BusinessException.class,
                () -> paymentService.updateStatus(2L, new UpdatePaymentStatusRequest("FAILED")));
        BusinessException refunded = assertThrows(BusinessException.class,
                () -> paymentService.updateStatus(3L, new UpdatePaymentStatusRequest("PENDING")));

        assertEquals("PAYMENT_STATUS_LOCKED", completed.getCode());
        assertEquals("PAYMENT_STATUS_LOCKED", refunded.getCode());
        verify(repository, never()).save(any());
    }

    @Test
    void unknownTargetStatusIsRejected() {
        payment(4L, "CASH", "PENDING");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.updateStatus(4L, new UpdatePaymentStatusRequest("PAID")));

        assertEquals("PAYMENT_STATUS_INVALID", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void pendingOrderPaymentCanBeCompletedByAdmin() {
        PaymentRecord payment = payment(5L, "CASH", "PENDING");

        assertEquals("COMPLETED", paymentService.updateStatus(5L, new UpdatePaymentStatusRequest("completed")).status());
        assertEquals("COMPLETED", payment.getStatus());
        verify(rabbitTemplate).convertAndSend(anyString(), eq(DomainEventNames.PAYMENT_COMPLETED), any(Object.class));
    }

    @Test
    void resendingCurrentStatusIsANoOp() {
        payment(6L, "CASH", "COMPLETED");

        assertEquals("COMPLETED", paymentService.updateStatus(6L, new UpdatePaymentStatusRequest("COMPLETED")).status());
        verify(repository, never()).save(any());
        verifyNoInteractions(rabbitTemplate);
    }

    private PaymentRecord payment(Long id, String method, String status) {
        PaymentRecord payment = new PaymentRecord();
        payment.setId(id);
        payment.setOrderId(100L + id);
        payment.setUserId(9L);
        payment.setAmount(BigDecimal.valueOf(20000));
        payment.setMethod(method);
        payment.setStatus(status);
        when(repository.findById(id)).thenReturn(Optional.of(payment));
        return payment;
    }
}
