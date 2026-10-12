package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.OrderSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.payment.client.NotificationClient;
import com.huynqb.laundrylocker.payment.client.OrderClient;
import com.huynqb.laundrylocker.payment.dto.CreateRefundRequest;
import com.huynqb.laundrylocker.payment.dto.SepayReturnStatusResponse;
import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import com.huynqb.laundrylocker.payment.model.RefundRecord;
import com.huynqb.laundrylocker.payment.repository.PaymentRepository;
import com.huynqb.laundrylocker.payment.repository.RefundRepository;
import com.huynqb.laundrylocker.payment.repository.UserBankAccountRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/// Trang quay về SePay chỉ báo trạng thái; thanh toán/hoàn tiền của đơn chỉ chủ đơn (hoặc ADMIN) xem/yêu cầu được.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceAccessTest {

    private static final long OWNER = 44L;
    private static final long STRANGER = 99L;
    private static final long ORDER = 55L;

    @Mock private PaymentRepository repository;
    @Mock private RefundRepository refundRepository;
    @Mock private UserBankAccountRepository userBankAccountRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private Environment environment;
    @Mock private WalletService walletService;
    @Mock private OrderClient orderClient;
    @Mock private NotificationClient notificationClient;
    @Mock private MomoService momoService;
    @Mock private SepayService sepayService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                repository, refundRepository, userBankAccountRepository, rabbitTemplate, environment,
                walletService, orderClient, notificationClient, momoService, sepayService,
                TestPaymentRules.defaults());
        when(orderClient.getOrder(ORDER))
                .thenReturn(ApiResponse.ok(new OrderSummary(ORDER, OWNER, "STORING", BigDecimal.valueOf(20000))));
        when(repository.save(any(PaymentRecord.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void sepayReturnOnlyReportsPendingTopup() {
        PaymentRecord topup = payment("TOPUP_44_1700000000000", "SEPAY_TOPUP", "PENDING", 0L, OWNER);
        when(repository.findByReferenceId("TOPUP_44_1700000000000")).thenReturn(Optional.of(topup));

        SepayReturnStatusResponse status = paymentService.sepayReturnStatus(
                Map.of("order_invoice_number", "TOPUP_44_1700000000000", "status", "success"));

        assertEquals("PENDING", status.status());
        assertFalse(status.paid());
        assertEquals("PENDING", topup.getStatus());
        verify(repository, never()).save(any());
        verifyNoInteractions(walletService, rabbitTemplate);
    }

    @Test
    void sepayReturnWithCancelLeavesPaymentUntouched() {
        PaymentRecord pending = payment("PAY-55-1", "SEPAY", "PENDING", ORDER, OWNER);
        when(repository.findByReferenceId("PAY-55-1")).thenReturn(Optional.of(pending));

        SepayReturnStatusResponse status = paymentService.sepayReturnStatus(
                Map.of("referenceId", "PAY-55-1", "status", "cancel"));

        assertEquals("PENDING", status.status());
        verify(repository, never()).save(any());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void sepayReturnReportsPaymentConfirmedByWebhook() {
        when(repository.findByReferenceId("PAY-55-1"))
                .thenReturn(Optional.of(payment("PAY-55-1", "SEPAY", "COMPLETED", ORDER, OWNER)));

        assertTrue(paymentService.sepayReturnStatus(Map.of("referenceId", "PAY-55-1")).paid());
    }

    @Test
    void sepayReturnWithoutReferenceIsRejected() {
        assertEquals("SEPAY_RETURN_INVALID", assertThrows(BusinessException.class,
                () -> paymentService.sepayReturnStatus(Map.of("status", "success"))).getCode());
    }

    @Test
    void verifiedWebhookStillCreditsTopup() {
        PaymentRecord topup = payment("TOPUP_44_1700000000000", "SEPAY_TOPUP", "PENDING", 0L, OWNER);
        Map<String, Object> body = Map.of("content", "TOPUP_44_1700000000000", "transferAmount", 50000);
        when(sepayService.verifyWebhook(eq(body), anyString())).thenReturn(true);
        when(sepayService.extractReferenceId(body)).thenReturn("TOPUP_44_1700000000000");
        when(sepayService.extractAmount(body)).thenReturn(BigDecimal.valueOf(50000));
        when(repository.findByReferenceId("TOPUP_44_1700000000000")).thenReturn(Optional.of(topup));

        paymentService.handleSepayWebhook(body, "Apikey key");

        assertEquals("COMPLETED", topup.getStatus());
        verify(walletService).credit(eq(OWNER), eq(BigDecimal.valueOf(50000)), eq(WalletService.SOURCE_TOPUP),
                eq("TOPUP_44_1700000000000"), anyString());
    }

    @Test
    void refundRequestForSomeoneElsesOrderIsForbidden() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> paymentService.requestRefund(STRANGER, refundRequest()));

        assertEquals("ORDER_FORBIDDEN", error.getCode());
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(refundRepository, never()).save(any());
        verify(orderClient, never()).updatePaymentStatus(anyLong(), anyString(), any(), any());
    }

    @Test
    void ownerRefundRequestPassesOwnershipCheck() {
        when(repository.findByOrderId(ORDER)).thenReturn(List.of());

        // Qua bước kiểm chủ đơn, dừng ở kiểm tra nghiệp vụ tiếp theo (đơn chưa thanh toán).
        assertEquals("ORDER_NOT_PAID", assertThrows(BusinessException.class,
                () -> paymentService.requestRefund(OWNER, refundRequest())).getCode());
    }

    @Test
    void orderPaymentsVisibleToOwnerAndAdminOnly() {
        when(repository.findByOrderId(ORDER)).thenReturn(List.of(payment("PAY-55-1", "SEPAY", "COMPLETED", ORDER, OWNER)));

        assertEquals(1, paymentService.listByOrder(ORDER, OWNER, "CUSTOMER").size());
        assertEquals("ORDER_FORBIDDEN", assertThrows(BusinessException.class,
                () -> paymentService.listByOrder(ORDER, STRANGER, "CUSTOMER")).getCode());
        assertEquals("AUTH_REQUIRED", assertThrows(BusinessException.class,
                () -> paymentService.listByOrder(ORDER, null, null)).getCode());
    }

    @Test
    void adminReadsOrderPaymentsWithoutOrderLookup() {
        when(repository.findByOrderId(ORDER)).thenReturn(List.of());
        when(refundRepository.findByOrderId(ORDER)).thenReturn(List.of());

        paymentService.listByOrder(ORDER, 1L, "ADMIN");
        paymentService.refundsByOrder(ORDER, 1L, "ADMIN");

        verify(orderClient, never()).getOrder(anyLong());
    }

    @Test
    void orderRefundsVisibleToOwnerOnly() {
        when(refundRepository.findByOrderId(ORDER)).thenReturn(List.of(refund(7L, 70L)));

        assertEquals(1, paymentService.refundsByOrder(ORDER, OWNER, "CUSTOMER").size());
        assertEquals("ORDER_FORBIDDEN", assertThrows(BusinessException.class,
                () -> paymentService.refundsByOrder(ORDER, STRANGER, "LOCKER_TECHNICIAN")).getCode());
    }

    @Test
    void singleRefundVisibleToPayerOrOrderOwner() {
        PaymentRecord paidByFriend = payment("PAY-55-2", "SEPAY", "COMPLETED", ORDER, 66L);
        paidByFriend.setId(70L);
        when(refundRepository.findById(7L)).thenReturn(Optional.of(refund(7L, 70L)));
        when(repository.findById(70L)).thenReturn(Optional.of(paidByFriend));

        assertEquals(7L, paymentService.getRefund(7L, 66L, "CUSTOMER").id());
        assertEquals(7L, paymentService.getRefund(7L, OWNER, "CUSTOMER").id());
        assertEquals("ORDER_FORBIDDEN", assertThrows(BusinessException.class,
                () -> paymentService.getRefund(7L, STRANGER, "CUSTOMER")).getCode());
    }

    @Test
    void singlePaymentVisibleToPayerOrOrderOwner() {
        PaymentRecord topup = payment("TOPUP_44_1", "SEPAY_TOPUP", "COMPLETED", 0L, OWNER);
        when(repository.findById(9L)).thenReturn(Optional.of(topup));

        assertEquals("TOPUP_44_1", paymentService.get(9L, OWNER, "CUSTOMER").referenceId());
        assertEquals("ORDER_FORBIDDEN", assertThrows(BusinessException.class,
                () -> paymentService.get(9L, STRANGER, "CUSTOMER")).getCode());
        assertEquals("TOPUP_44_1", paymentService.get(9L, 1L, "ADMIN").referenceId());
        verify(orderClient, never()).getOrder(anyLong());
    }

    private static PaymentRecord payment(String ref, String method, String status, Long orderId, Long userId) {
        PaymentRecord payment = new PaymentRecord();
        payment.setReferenceId(ref);
        payment.setMethod(method);
        payment.setStatus(status);
        payment.setOrderId(orderId);
        payment.setUserId(userId);
        payment.setAmount(BigDecimal.valueOf(50000));
        return payment;
    }

    private static RefundRecord refund(Long id, Long paymentId) {
        RefundRecord refund = new RefundRecord();
        refund.setId(id);
        refund.setPaymentId(paymentId);
        refund.setOrderId(ORDER);
        refund.setAmount(BigDecimal.valueOf(20000));
        refund.setStatus("PENDING");
        return refund;
    }

    private static CreateRefundRequest refundRequest() {
        return new CreateRefundRequest(ORDER, null, "Đổi ý", "VCB", "VCB", "0011", "NGUYEN AN", false);
    }
}
