package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.OrderSummary;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.payment.client.NotificationClient;
import com.huynqb.laundrylocker.payment.client.OrderClient;
import com.huynqb.laundrylocker.payment.dto.CheckoutRequest;
import com.huynqb.laundrylocker.payment.dto.CreateRefundRequest;
import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import com.huynqb.laundrylocker.payment.model.RefundRecord;
import com.huynqb.laundrylocker.payment.model.WalletTransaction;
import com.huynqb.laundrylocker.payment.repository.PaymentRepository;
import com.huynqb.laundrylocker.payment.repository.RefundRepository;
import com.huynqb.laundrylocker.payment.repository.UserBankAccountRepository;
import com.huynqb.laundrylocker.payment.settings.PaymentSettingsCatalog;
import com.huynqb.laundrylocker.payment.settings.TestPaymentRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Đối soát tiền: chuyển khoản thiếu/lặp/tiền ra, trả ví nhiều lần cho một đơn, giới hạn và trạng thái hoàn tiền.
@ExtendWith(MockitoExtension.class)
class PaymentServiceMoneyRulesTest {

    private static final String API_KEY = "test-key";

    @Mock private PaymentRepository repository;
    @Mock private RefundRepository refundRepository;
    @Mock private UserBankAccountRepository userBankAccountRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private WalletService walletService;
    @Mock private OrderClient orderClient;
    @Mock private NotificationClient notificationClient;

    private PaymentService service;
    private final List<PaymentRecord> payments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        SepayService sepay = new SepayService();
        ReflectionTestUtils.setField(sepay, "apiKey", API_KEY);
        service = new PaymentService(
                repository, refundRepository, userBankAccountRepository, rabbitTemplate, walletService,
                orderClient, notificationClient, sepay,
                TestPaymentRules.of(Map.of(PaymentSettingsCatalog.ENABLED_METHODS, "CASH,WALLET,SEPAY")));
        lenient().when(repository.findByOrderId(55L)).thenAnswer(invocation -> List.copyOf(payments));
        lenient().when(repository.save(any(PaymentRecord.class))).thenAnswer(invocation -> {
            PaymentRecord saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(100L + payments.size());
                payments.add(saved);
            }
            return saved;
        });
        lenient().when(orderClient.getOrder(55L))
                .thenReturn(ApiResponse.ok(new OrderSummary(55L, 44L, "INITIALIZED", BigDecimal.valueOf(15000))));
    }

    // ------------------------------------------------------------------ webhook SePay

    /// Đơn 15.000 đ mà khách chuyển 1.000 đ: khoản đó ghi đúng 1.000 đ (order-service cộng dồn nên đơn
    /// vẫn chưa PAID), không được ghi thành khoản trả đủ.
    @Test
    void underpaidTransferIsRecordedAtTheAmountActuallyReceived() {
        service.handleSepayWebhook(transfer("PAY-55", "1000", "FT001"), "Apikey " + API_KEY);

        PaymentRecord recorded = payments.get(0);
        assertEquals("COMPLETED", recorded.getStatus());
        assertEquals(0, BigDecimal.valueOf(1000).compareTo(recorded.getAmount()));
        assertTrue(recorded.getDescription().contains("Chuyển thiếu"), recorded.getDescription());
        assertEquals(0, BigDecimal.valueOf(1000).compareTo((BigDecimal) publishedPayload().get("amount")));
    }

    /// Khách chuyển bù phần còn thiếu với cùng nội dung: là khoản mới của đơn, không bị coi là webhook lặp.
    @Test
    void topUpTransferWithTheSameContentBecomesANewPayment() {
        service.handleSepayWebhook(transfer("PAY-55", "1000", "FT001"), "Apikey " + API_KEY);
        when(repository.findByReferenceId("PAY-55")).thenReturn(Optional.of(payments.get(0)));

        service.handleSepayWebhook(transfer("PAY-55", "14000", "FT002"), "Apikey " + API_KEY);

        assertEquals(2, payments.size());
        PaymentRecord second = payments.get(1);
        assertEquals("PAY-55-FT002", second.getReferenceId());
        assertEquals("COMPLETED", second.getStatus());
        // Phần còn phải trả là 14.000 đ (server tính), khách chuyển đúng số đó.
        assertEquals(0, BigDecimal.valueOf(14000).compareTo(second.getAmount()));
    }

    @Test
    void sameBankTransactionDeliveredTwiceIsRecordedOnce() {
        PaymentRecord done = new PaymentRecord();
        done.setId(9L);
        done.setStatus("COMPLETED");
        when(repository.findFirstByReferenceTransactionId("FT001")).thenReturn(Optional.of(done));

        service.handleSepayWebhook(transfer("PAY-55", "15000", "FT001"), "Apikey " + API_KEY);

        verify(repository, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    /// Tiền chuyển ĐI (vd admin chuyển hoàn, nội dung vẫn có PAY-55) không phải tiền khách trả.
    @Test
    void outgoingTransferIsIgnored() {
        Map<String, Object> body = transfer("RF-PAY-55-1", "15000", "FT009");
        body.put("transferType", "out");

        assertNull(service.handleSepayWebhook(body, "Apikey " + API_KEY));

        verify(repository, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void webhookWithoutAmountDoesNotMarkAnythingPaid() {
        Map<String, Object> body = transfer("PAY-55", "0", "FT010");

        service.handleSepayWebhook(body, "Apikey " + API_KEY);

        assertEquals("PENDING", payments.get(0).getStatus());
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    // ------------------------------------------------------------------ trả bằng ví

    /// Lần trả ví thứ hai cho cùng đơn (gia hạn / phí quá hạn) phải trừ tiền với mã chống trùng mới.
    @Test
    void secondWalletPaymentForTheSameOrderIsDebitedToo() {
        PaymentRecord first = new PaymentRecord();
        first.setId(1L);
        first.setOrderId(55L);
        first.setMethod("WALLET");
        first.setStatus("COMPLETED");
        first.setAmount(BigDecimal.valueOf(10000));
        payments.add(first);
        when(walletService.debit(eq(44L), any(), any(), eq("ORDERPAY-55-2"), any()))
                .thenReturn(new WalletTransaction());

        service.checkout(44L, new CheckoutRequest(55L, "WALLET", null, null));

        verify(walletService).debit(eq(44L), eq(BigDecimal.valueOf(5000)), eq(WalletService.SOURCE_ORDER_PAYMENT),
                eq("ORDERPAY-55-2"), any());
        assertEquals("COMPLETED", payments.get(1).getStatus());
    }

    /// Hai yêu cầu trả cùng lúc ra cùng mã: lần sau không được ghi là đã trả mà không trừ tiền.
    @Test
    void duplicateWalletDebitIsRejectedInsteadOfMarkedPaid() {
        when(walletService.debit(eq(44L), any(), any(), eq("ORDERPAY-55"), any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.checkout(44L, new CheckoutRequest(55L, "WALLET", null, null)));

        assertEquals("PAYMENT_IN_PROGRESS", error.getCode());
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void walletTransactionRefsStillPointToTheOrder() {
        WalletTransaction tx = new WalletTransaction();
        tx.setSource(WalletService.SOURCE_ORDER_PAYMENT);
        tx.setReferenceId("ORDERPAY-55-3");
        assertEquals(55L, WalletTransactionRefs.relatedOrderId(tx));
        tx.setReferenceId("ORDERPAY-55");
        assertEquals(55L, WalletTransactionRefs.relatedOrderId(tx));
    }

    // ------------------------------------------------------------------ hoàn tiền

    @Test
    void customerCannotAskForMoreThanTheOrderPaid() {
        payments.add(completed(15000));
        when(refundRepository.findByOrderId(55L)).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.requestRefund(44L, refundRequest(BigDecimal.valueOf(1_000_000))));

        assertEquals("REFUND_AMOUNT_EXCEEDS_PAID", error.getCode());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void refundStatusOfTheOrderFollowsHowMuchWasRefunded() {
        BigDecimal paid = BigDecimal.valueOf(15000);
        assertEquals("REFUND_PENDING", PaymentService.orderRefundStatus(List.of(refund("PENDING", 15000)), paid));
        assertEquals("PAID", PaymentService.orderRefundStatus(List.of(refund("COMPLETED", 5000)), paid));
        assertEquals("REFUNDED", PaymentService.orderRefundStatus(List.of(refund("COMPLETED", 15000)), paid));
        assertEquals("PAID", PaymentService.orderRefundStatus(List.of(refund("REJECTED", 15000)), paid));
    }

    /// Duyệt hoàn một phần: đơn trở về PAID (không REFUNDED — không trả lại mã giảm giá/điểm đã dùng),
    /// và báo loyalty-service số tiền đã hoàn để thu hồi điểm tương ứng.
    @Test
    void approvingAPartialRefundKeepsTheOrderPaidAndReportsTheRefundedAmount() {
        payments.add(completed(15000));
        RefundRecord pending = refund("PENDING", 5000);
        pending.setId(3L);
        pending.setOrderId(55L);
        pending.setPaymentId(payments.get(0).getId());
        when(refundRepository.findById(3L)).thenReturn(Optional.of(pending));
        when(refundRepository.save(any(RefundRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refundRepository.findByOrderId(55L)).thenReturn(List.of(pending));
        when(repository.findById(any())).thenReturn(Optional.empty());

        service.adminApproveRefund(3L, 1L, null);

        verify(orderClient).updatePaymentStatus(eq(55L), eq("PAID"), any(), eq(1L));
        Map<String, Object> payload = publishedPayload(DomainEventNames.PAYMENT_REFUNDED);
        assertEquals(0, BigDecimal.valueOf(5000).compareTo((BigDecimal) payload.get("refundedAmount")));
        assertEquals(0, BigDecimal.valueOf(15000).compareTo((BigDecimal) payload.get("paidAmount")));
    }

    // ------------------------------------------------------------------ giả lập thanh toán

    @Test
    void simulationIsRejectedWhileAdminHasItTurnedOff() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.simulateTransfer(44L, "CUSTOMER", 1L, null, null));

        assertEquals("PAYMENT_SIMULATION_DISABLED", error.getCode());
    }

    /// Bật giả lập: giao dịch VietQR đang chờ được ghi như webhook thật (đủ số hoặc một phần).
    @Test
    void simulatedTransferCompletesThePendingPaymentLikeARealOne() {
        PaymentService simulating = withSimulation("44");
        PaymentRecord pending = new PaymentRecord();
        pending.setOrderId(55L);
        pending.setUserId(44L);
        pending.setMethod("SEPAY");
        pending.setStatus("PENDING");
        pending.setReferenceId("PAY-55-9");
        pending.setDescription("Thanh toán đơn #55");
        pending.setAmount(BigDecimal.valueOf(15000));
        repository.save(pending);
        when(repository.findById(pending.getId())).thenReturn(Optional.of(pending));

        simulating.simulateTransfer(44L, "CUSTOMER", pending.getId(), null, BigDecimal.valueOf(1000));

        assertEquals("COMPLETED", pending.getStatus());
        assertEquals(0, BigDecimal.valueOf(1000).compareTo(pending.getAmount()));
        assertTrue(pending.getDescription().startsWith("[Giả lập]"), pending.getDescription());
        assertTrue(pending.getReferenceTransactionId().startsWith("SIMULATED-"));
        assertEquals(0, BigDecimal.valueOf(1000).compareTo((BigDecimal) publishedPayload().get("amount")));
    }

    @Test
    void simulationOnlyForAllowedAccountsAndTheirOwnPayments() {
        PaymentService simulating = withSimulation("44");
        PaymentRecord pending = new PaymentRecord();
        pending.setUserId(45L);
        pending.setMethod("SEPAY");
        pending.setStatus("PENDING");
        pending.setAmount(BigDecimal.valueOf(15000));
        repository.save(pending);
        when(repository.findById(pending.getId())).thenReturn(Optional.of(pending));

        assertEquals("PAYMENT_SIMULATION_DISABLED", assertThrows(BusinessException.class,
                () -> simulating.simulateTransfer(45L, "CUSTOMER", pending.getId(), null, null)).getCode());
        assertEquals("ORDER_FORBIDDEN", assertThrows(BusinessException.class,
                () -> simulating.simulateTransfer(44L, "CUSTOMER", pending.getId(), null, null)).getCode());
        assertEquals("PENDING", pending.getStatus());
    }

    private PaymentService withSimulation(String allowedUserIds) {
        return new PaymentService(
                repository, refundRepository, userBankAccountRepository, rabbitTemplate, walletService,
                orderClient, notificationClient, new SepayService(),
                TestPaymentRules.of(Map.of(
                        PaymentSettingsCatalog.ENABLED_METHODS, "CASH,WALLET,SEPAY",
                        PaymentSettingsCatalog.SIMULATION_ENABLED, true,
                        PaymentSettingsCatalog.SIMULATION_ALLOWED_USER_IDS, allowedUserIds)));
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> transfer(String content, String amount, String referenceCode) {
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        body.put("transferType", "in");
        body.put("transferAmount", amount);
        body.put("referenceCode", referenceCode);
        return body;
    }

    private PaymentRecord completed(long amount) {
        PaymentRecord payment = new PaymentRecord();
        payment.setId(50L + payments.size());
        payment.setOrderId(55L);
        payment.setUserId(44L);
        payment.setMethod("SEPAY");
        payment.setStatus("COMPLETED");
        payment.setReferenceId("PAY-55-1");
        payment.setAmount(BigDecimal.valueOf(amount));
        payment.setCreatedAt(java.time.LocalDateTime.now());
        return payment;
    }

    private static RefundRecord refund(String status, long amount) {
        RefundRecord refund = new RefundRecord();
        refund.setStatus(status);
        refund.setAmount(BigDecimal.valueOf(amount));
        refund.setTransactionId("RF-PAY-55-1");
        return refund;
    }

    private static CreateRefundRequest refundRequest(BigDecimal amount) {
        return new CreateRefundRequest(55L, amount, "Đổi ý", "VCB", "VCB", "0123456789", "NGUYEN VAN A", null, false);
    }

    private Map<String, Object> publishedPayload() {
        return publishedPayload(DomainEventNames.PAYMENT_COMPLETED);
    }

    private Map<String, Object> publishedPayload(String eventName) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(DomainEventNames.EXCHANGE), eq(eventName), captor.capture());
        return ((DomainEvent) captor.getValue()).payload();
    }
}
