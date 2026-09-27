package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.payment.dto.TransactionMethodTotalResponse;
import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import com.huynqb.laundrylocker.payment.model.Wallet;
import com.huynqb.laundrylocker.payment.model.WalletTransaction;
import com.huynqb.laundrylocker.payment.repository.PaymentRepository;
import com.huynqb.laundrylocker.payment.repository.RefundRepository;
import com.huynqb.laundrylocker.payment.repository.WalletRepository;
import com.huynqb.laundrylocker.payment.repository.WalletTransactionRepository;
import com.huynqb.laundrylocker.payment.repository.WithdrawalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTotalByMethodTest {

    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository transactionRepository;
    @Mock private WithdrawalRepository withdrawalRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private PaymentReferenceResolver resolver;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(
                walletRepository,
                transactionRepository,
                withdrawalRepository,
                paymentRepository,
                refundRepository,
                resolver
        );
    }

    @Test
    void calculateTotalByMethod_allMethods_aggregatesCorrectly() {
        Long userId = 42L;
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setBalance(BigDecimal.valueOf(200000));
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        // 1 wallet order payment (debit, 50k)
        WalletTransaction tx1 = new WalletTransaction();
        tx1.setId(1L);
        tx1.setUserId(userId);
        tx1.setType("DEBIT");
        tx1.setAmount(BigDecimal.valueOf(50000));
        tx1.setSource(WalletService.SOURCE_ORDER_PAYMENT);
        tx1.setReferenceId("ORDER-101");
        tx1.setDescription("Thanh toán đơn #101");
        tx1.setCreatedAt(LocalDateTime.now().minusHours(2));

        // 1 sepay topup (credit, 100k)
        WalletTransaction tx2 = new WalletTransaction();
        tx2.setId(2L);
        tx2.setUserId(userId);
        tx2.setType("CREDIT");
        tx2.setAmount(BigDecimal.valueOf(100000));
        tx2.setSource(WalletService.SOURCE_TOPUP);
        tx2.setReferenceId("SEPAY-12345");
        tx2.setDescription("Nạp ví qua SePay");
        tx2.setCreatedAt(LocalDateTime.now().minusHours(1));

        when(transactionRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(tx2, tx1));

        // 1 direct payment via SePay (e.g. 70k)
        PaymentRecord directPay = new PaymentRecord();
        directPay.setId(10L);
        directPay.setUserId(userId);
        directPay.setOrderId(102L);
        directPay.setAmount(BigDecimal.valueOf(70000));
        directPay.setMethod("SEPAY");
        directPay.setStatus("COMPLETED");
        directPay.setReferenceId("SEPAY-DIRECT-1");
        directPay.setDescription("Thanh toán SePay QR");
        directPay.setCreatedAt(LocalDateTime.now().minusMinutes(30));

        when(paymentRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(directPay));
        when(resolver.orders(any())).thenReturn(Map.of());

        // Test with method="ALL", period="ALL"
        TransactionMethodTotalResponse response = walletService.calculateTotalByMethod(userId, "ALL", "ALL", null, null);

        assertNotNull(response);
        assertEquals("ALL", response.selectedMethod());
        assertEquals(3, response.transactionCount());
        // totalAmount = 50k + 100k + 70k = 220k
        assertEquals(0, BigDecimal.valueOf(220000).compareTo(response.totalAmount()));
        // totalExpense = 50k + 70k = 120k
        assertEquals(0, BigDecimal.valueOf(120000).compareTo(response.totalExpense()));
        // totalIncome = 100k
        assertEquals(0, BigDecimal.valueOf(100000).compareTo(response.totalIncome()));

        // Check summary map
        // WALLET = 50k
        assertEquals(0, BigDecimal.valueOf(50000).compareTo(response.summary().get("WALLET")));
        // SEPAY = 100k (topup) + 70k (direct) = 170k
        assertEquals(0, BigDecimal.valueOf(170000).compareTo(response.summary().get("SEPAY")));

        // Test with method="SEPAY"
        TransactionMethodTotalResponse sepayResponse = walletService.calculateTotalByMethod(userId, "SEPAY", "ALL", null, null);
        assertEquals("SEPAY", sepayResponse.selectedMethod());
        assertEquals(2, sepayResponse.transactionCount());
        assertEquals(0, BigDecimal.valueOf(170000).compareTo(sepayResponse.totalAmount()));
        assertEquals(0, BigDecimal.valueOf(70000).compareTo(sepayResponse.totalExpense()));
        assertEquals(0, BigDecimal.valueOf(100000).compareTo(sepayResponse.totalIncome()));
    }

    @Test
    void calculateTotalByMethod_dateRange_filtersCorrectly() {
        Long userId = 42L;
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setBalance(BigDecimal.ZERO);
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        // tx yesterday
        WalletTransaction txOld = new WalletTransaction();
        txOld.setId(1L);
        txOld.setUserId(userId);
        txOld.setType("DEBIT");
        txOld.setAmount(BigDecimal.valueOf(30000));
        txOld.setSource(WalletService.SOURCE_ORDER_PAYMENT);
        txOld.setReferenceId("OLD-1");
        txOld.setDescription("Order old");
        txOld.setCreatedAt(LocalDateTime.now().minusDays(5));

        // tx today
        WalletTransaction txToday = new WalletTransaction();
        txToday.setId(2L);
        txToday.setUserId(userId);
        txToday.setType("DEBIT");
        txToday.setAmount(BigDecimal.valueOf(20000));
        txToday.setSource(WalletService.SOURCE_ORDER_PAYMENT);
        txToday.setReferenceId("TODAY-1");
        txToday.setDescription("Order today");
        txToday.setCreatedAt(LocalDateTime.now());

        when(transactionRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(txToday, txOld));
        when(paymentRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(resolver.orders(any())).thenReturn(Map.of());

        // Filter DAY
        TransactionMethodTotalResponse dayResp = walletService.calculateTotalByMethod(userId, "ALL", "DAY", null, null);
        assertEquals(1, dayResp.transactionCount());
        assertEquals(0, BigDecimal.valueOf(20000).compareTo(dayResp.totalAmount()));
    }
}
