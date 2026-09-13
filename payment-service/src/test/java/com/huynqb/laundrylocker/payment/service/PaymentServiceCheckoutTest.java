package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.OrderSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.payment.client.OrderClient;
import com.huynqb.laundrylocker.payment.dto.CheckoutRequest;
import com.huynqb.laundrylocker.payment.dto.PaymentResponse;
import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import com.huynqb.laundrylocker.payment.repository.PaymentRepository;
import com.huynqb.laundrylocker.payment.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceCheckoutTest {

    @Mock private PaymentRepository repository;
    @Mock private RefundRepository refundRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private Environment environment;
    @Mock private WalletService walletService;
    @Mock private OrderClient orderClient;
    @Mock private MomoService momoService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                repository,
                refundRepository,
                rabbitTemplate,
                environment,
                walletService,
                orderClient,
                momoService);
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

        PaymentResponse response = paymentService.checkout(44L, new CheckoutRequest(55L, "CASH", null, null, null));

        assertEquals(BigDecimal.valueOf(5000), response.amount());

        ArgumentCaptor<PaymentRecord> captor = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(repository).save(captor.capture());
        assertEquals(BigDecimal.valueOf(5000), captor.getValue().getAmount());
        verify(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));
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
                () -> paymentService.checkout(44L, new CheckoutRequest(55L, "CASH", null, null, null)));

        assertEquals("ORDER_ALREADY_PAID", error.getCode());
    }
}
