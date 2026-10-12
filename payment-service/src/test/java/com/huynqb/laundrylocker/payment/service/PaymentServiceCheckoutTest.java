package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.OrderSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.payment.client.OrderClient;
import com.huynqb.laundrylocker.payment.dto.CheckoutRequest;
import com.huynqb.laundrylocker.payment.dto.CreatePaymentRequest;
import com.huynqb.laundrylocker.payment.dto.CreateTopupRequest;
import com.huynqb.laundrylocker.payment.dto.PaymentResponse;
import com.huynqb.laundrylocker.payment.dto.TopupResponse;
import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import com.huynqb.laundrylocker.payment.repository.PaymentRepository;
import com.huynqb.laundrylocker.payment.repository.RefundRepository;
import com.huynqb.laundrylocker.payment.settings.PaymentRules;
import com.huynqb.laundrylocker.payment.settings.TestPaymentRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceCheckoutTest {

    @Mock private PaymentRepository repository;
    @Mock private RefundRepository refundRepository;
    @Mock private com.huynqb.laundrylocker.payment.repository.UserBankAccountRepository userBankAccountRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private WalletService walletService;
    @Mock private OrderClient orderClient;
    @Mock private com.huynqb.laundrylocker.payment.client.NotificationClient notificationClient;
    @Mock private SepayService sepayService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                repository,
                refundRepository,
                userBankAccountRepository,
                rabbitTemplate,
                walletService,
                orderClient,
                notificationClient,
                sepayService,
                TestPaymentRules.defaults());
    }

    @Test
    void checkoutChargesOnlyOutstandingBalanceAfterRentalExtension() {
        when(orderClient.getOrder(55L)).thenReturn(ApiResponse.ok(new OrderSummary(55L, 44L, "STORING", BigDecimal.valueOf(15000))));

        PaymentRecord completed = new PaymentRecord();
        completed.setOrderId(55L);
        completed.setAmount(BigDecimal.valueOf(10000));
        completed.setStatus("COMPLETED");
        when(repository.findByOrderId(55L)).thenReturn(List.of(completed));
        when(repository.save(any(PaymentRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.checkout(44L, new CheckoutRequest(55L, "CASH", null, null));

        assertEquals(BigDecimal.valueOf(5000), response.amount());

        ArgumentCaptor<PaymentRecord> captor = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(repository).save(captor.capture());
        assertEquals(BigDecimal.valueOf(5000), captor.getValue().getAmount());
        verify(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    /// Thuê rồi gia hạn sinh hai PaymentRecord trên cùng một đơn. Trước đây cả hai mang
    /// mô tả y hệt "Thanh toán đơn #55" nên chi tiết đơn hiện hai khối tiền giống nhau,
    /// khách không biết khoản nào là tiền thuê, khoản nào là tiền gia hạn.
    @Test
    void topUpAfterExtensionIsLabelledAsAnAdditionalPayment() {
        when(orderClient.getOrder(55L)).thenReturn(ApiResponse.ok(new OrderSummary(55L, 44L, "STORING", BigDecimal.valueOf(15000))));

        PaymentRecord completed = new PaymentRecord();
        completed.setOrderId(55L);
        completed.setAmount(BigDecimal.valueOf(10000));
        completed.setStatus("COMPLETED");
        when(repository.findByOrderId(55L)).thenReturn(List.of(completed));
        when(repository.save(any(PaymentRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.checkout(44L, new CheckoutRequest(55L, "CASH", null, null));

        ArgumentCaptor<PaymentRecord> captor = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(repository).save(captor.capture());
        assertEquals("Thanh toán bổ sung đơn #55", captor.getValue().getDescription());
    }

    @Test
    void firstPaymentKeepsThePlainOrderLabel() {
        assertEquals("Thanh toán đơn #55", PaymentService.paymentPurpose(null, 55L, BigDecimal.ZERO));
    }

    /// App gửi được lý do cụ thể thì ưu tiên; chuỗi rỗng coi như không gửi.
    @Test
    void clientReasonWinsOverTheDerivedOne() {
        assertEquals("Phí quá hạn",
                PaymentService.paymentPurpose("  Phí quá hạn  ", 55L, BigDecimal.valueOf(10000)));
        assertEquals("Thanh toán đơn #55", PaymentService.paymentPurpose("   ", 55L, BigDecimal.ZERO));
    }

    /// Chuỗi do client gửi hiện thẳng lên màn hình khách nên phải có trần.
    @Test
    void clientReasonIsCappedBecauseItIsShownToTheCustomer() {
        assertEquals(120, PaymentService.paymentPurpose("x".repeat(200), 55L, BigDecimal.ZERO).length());
    }

    @Test
    void paymentResponseExposesContentAndTimestamps() {
        PaymentRecord record = new PaymentRecord();
        record.setId(9L);
        record.setOrderId(55L);
        record.setUserId(44L);
        record.setMethod("CASH");
        record.setStatus("COMPLETED");
        record.setContent("Thanh toan don 55");
        record.setCreatedAt(java.time.LocalDateTime.of(2026, 9, 15, 1, 2, 3));
        record.setUpdatedAt(java.time.LocalDateTime.of(2026, 9, 15, 1, 5, 0));
        when(repository.findById(9L)).thenReturn(java.util.Optional.of(record));

        PaymentResponse response = paymentService.get(9L);

        assertEquals("Thanh toan don 55", response.content());
        assertEquals(java.time.LocalDateTime.of(2026, 9, 15, 1, 2, 3), response.createdAt());
        assertEquals(java.time.LocalDateTime.of(2026, 9, 15, 1, 5, 0), response.updatedAt());
    }

    @Test
    void checkoutStillRejectsOrderWhenCompletedPaymentsAlreadyCoverTotal() {
        when(orderClient.getOrder(55L)).thenReturn(ApiResponse.ok(new OrderSummary(55L, 44L, "STORING", BigDecimal.valueOf(15000))));

        PaymentRecord completed = new PaymentRecord();
        completed.setOrderId(55L);
        completed.setAmount(BigDecimal.valueOf(15000));
        completed.setStatus("COMPLETED");
        when(repository.findByOrderId(55L)).thenReturn(List.of(completed));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> paymentService.checkout(44L, new CheckoutRequest(55L, "CASH", null, null)));

        assertEquals("ORDER_ALREADY_PAID", error.getCode());
    }

    @Test
    void checkoutRejectsMethodDisabledByAdminBeforeLookingUpOrder() {
        PaymentService service = serviceWith(Map.of("app.payment.enabled-methods", "WALLET, sepay"));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.checkout(44L, new CheckoutRequest(55L, "CASH", null, null)));

        assertEquals("PAYMENT_METHOD_DISABLED", error.getCode());
        verify(orderClient, never()).getOrder(any());
    }

    @Test
    void cashStaysPendingWhenAutoCompleteIsTurnedOff() {
        PaymentService service = serviceWith(Map.of("app.payment.cash-auto-complete", false));
        when(orderClient.getOrder(55L)).thenReturn(ApiResponse.ok(new OrderSummary(55L, 44L, "STORING", BigDecimal.valueOf(15000))));
        when(repository.findByOrderId(55L)).thenReturn(List.of());
        when(repository.save(any(PaymentRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = service.checkout(44L, new CheckoutRequest(55L, "CASH", null, null));

        assertEquals("PENDING", response.status());
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void topupAmountMustStayWithinAdminLimits() {
        PaymentService service = serviceWith(Map.of(
                "app.payment.topup-min-amount", 10_000,
                "app.payment.topup-max-amount", 2_000_000));

        assertEquals("TOPUP_AMOUNT_OUT_OF_RANGE", assertThrows(
                BusinessException.class,
                () -> service.createTopupUrl(44L, new CreateTopupRequest(BigDecimal.valueOf(5_000), null, null)))
                .getCode());
        assertEquals("TOPUP_AMOUNT_OUT_OF_RANGE", assertThrows(
                BusinessException.class,
                () -> service.createTopupUrl(44L, new CreateTopupRequest(BigDecimal.valueOf(3_000_000), null, null)))
                .getCode());
        verify(repository, never()).save(any(PaymentRecord.class));
    }

    /// VNPay/MoMo đã ngừng hỗ trợ: thanh toán đơn bằng hai phương thức này bị từ chối 400 trước khi tra đơn,
    /// kể cả khi giá trị cấu hình cũ còn liệt kê chúng.
    @Test
    void checkoutRejectsRetiredMethodsBeforeLookingUpOrder() {
        for (String method : new String[]{"VNPAY", "momo", " MoMo"}) {
            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> paymentService.checkout(44L, new CheckoutRequest(55L, method, null, null)),
                    method);

            assertEquals("PAYMENT_METHOD_UNSUPPORTED", error.getCode(), method);
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatus(), method);
            assertTrue(error.getMessage().contains("không còn được hỗ trợ"), error.getMessage());
        }
        verify(orderClient, never()).getOrder(any());
        verify(repository, never()).save(any(PaymentRecord.class));
    }

    @Test
    void directPaymentCreationRejectsRetiredMethods() {
        for (String method : new String[]{"VNPAY", "MOMO", "vnpay_topup"}) {
            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> paymentService.create(new CreatePaymentRequest(55L, 44L, BigDecimal.TEN, method, null, null)),
                    method);

            assertEquals("PAYMENT_METHOD_UNSUPPORTED", error.getCode(), method);
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatus(), method);
        }
        verify(repository, never()).save(any(PaymentRecord.class));
    }

    @Test
    void topupRejectsRetiredMethods() {
        for (String method : new String[]{"VNPAY", "momo"}) {
            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> paymentService.createTopupUrl(44L, new CreateTopupRequest(BigDecimal.valueOf(50_000), null, method)),
                    method);

            assertEquals("PAYMENT_METHOD_UNSUPPORTED", error.getCode(), method);
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatus(), method);
        }
        assertEquals("PAYMENT_METHOD_INVALID", assertThrows(
                BusinessException.class,
                () -> paymentService.createTopupUrl(44L, new CreateTopupRequest(BigDecimal.valueOf(50_000), null, "WALLET")))
                .getCode());
        verify(repository, never()).save(any(PaymentRecord.class));
        verify(sepayService, never()).createPayment(any(), any());
    }

    /// Bỏ trống `method` ⇒ nạp qua SePay (trước đây mặc định VNPay).
    @Test
    void topupWithoutMethodGoesThroughSepay() {
        when(sepayService.createPayment(any(PaymentRecord.class), any())).thenReturn("https://pay.example/sepay");

        TopupResponse response = paymentService.createTopupUrl(
                44L, new CreateTopupRequest(BigDecimal.valueOf(50_000), null, null));

        assertEquals("https://pay.example/sepay", response.paymentUrl());
        ArgumentCaptor<PaymentRecord> captor = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(repository).save(captor.capture());
        assertEquals("SEPAY_TOPUP", captor.getValue().getMethod());
        assertEquals(0L, captor.getValue().getOrderId());
        assertEquals(44L, captor.getValue().getUserId());
    }

    @Test
    void refundOrderCreatesPendingRefundWithoutWalletCredit() {
        PaymentRecord paid = new PaymentRecord();
        paid.setId(7L);
        paid.setOrderId(55L);
        paid.setUserId(44L);
        paid.setAmount(BigDecimal.valueOf(15000));
        paid.setStatus("COMPLETED");
        paid.setReferenceId("PAY-55-1");
        PaymentRecord pending = new PaymentRecord();
        pending.setId(8L);
        pending.setOrderId(55L);
        pending.setUserId(44L);
        pending.setAmount(BigDecimal.valueOf(15000));
        pending.setStatus("PENDING");
        when(repository.findByOrderId(55L)).thenReturn(List.of(paid, pending));
        when(refundRepository.findByPaymentIdIn(List.of(7L))).thenReturn(List.of());

        var result = paymentService.refundOrder(55L, "Đơn drone bị huỷ", 99L);

        assertEquals(0, BigDecimal.valueOf(15000).compareTo(result.refundedAmount()));
        assertEquals(1, result.refundedPayments());
        verify(refundRepository).save(org.mockito.ArgumentMatchers.argThat(refund ->
                refund.getPaymentId().equals(7L) && "PENDING".equals(refund.getStatus())));
        // Không hoàn tiền về ví nữa; chờ admin duyệt chuyển khoản ngân hàng.
        verify(walletService, org.mockito.Mockito.never()).credit(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refundOrderSkipsPaymentsAlreadyRefunded() {
        PaymentRecord paid = new PaymentRecord();
        paid.setId(7L);
        paid.setOrderId(55L);
        paid.setUserId(44L);
        paid.setAmount(BigDecimal.valueOf(15000));
        paid.setStatus("COMPLETED");
        paid.setReferenceId("PAY-55-1");
        com.huynqb.laundrylocker.payment.model.RefundRecord done =
                new com.huynqb.laundrylocker.payment.model.RefundRecord();
        done.setPaymentId(7L);
        done.setStatus("COMPLETED");
        when(repository.findByOrderId(55L)).thenReturn(List.of(paid));
        when(refundRepository.findByPaymentIdIn(List.of(7L))).thenReturn(List.of(done));

        var result = paymentService.refundOrder(55L, "Đơn drone bị huỷ", 99L);

        assertEquals(0, result.refundedPayments());
        verify(refundRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
        verify(walletService, org.mockito.Mockito.never()).credit(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private PaymentService serviceWith(Map<String, ?> overrides) {
        return new PaymentService(
                repository, refundRepository, userBankAccountRepository, rabbitTemplate,
                walletService, orderClient, notificationClient, sepayService, TestPaymentRules.of(overrides));
    }
}
