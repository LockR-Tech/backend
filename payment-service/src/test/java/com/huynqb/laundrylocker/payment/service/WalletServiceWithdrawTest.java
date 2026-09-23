package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.payment.dto.WithdrawRequest;
import com.huynqb.laundrylocker.payment.dto.WithdrawResponse;
import com.huynqb.laundrylocker.payment.model.Wallet;
import com.huynqb.laundrylocker.payment.model.WalletTransaction;
import com.huynqb.laundrylocker.payment.model.WithdrawalRecord;
import com.huynqb.laundrylocker.payment.repository.PaymentRepository;
import com.huynqb.laundrylocker.payment.repository.RefundRepository;
import com.huynqb.laundrylocker.payment.repository.WalletRepository;
import com.huynqb.laundrylocker.payment.repository.WalletTransactionRepository;
import com.huynqb.laundrylocker.payment.repository.WithdrawalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceWithdrawTest {

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
    void getWithdrawableBalance_returnsZero_whenNoRealSePayPaymentsExist() {
        Long userId = 100L;
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setBalance(BigDecimal.valueOf(500000)); // Admin added 500k test money
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(paymentRepository.sumRealTopupsByUserId(userId)).thenReturn(BigDecimal.ZERO);
        when(refundRepository.sumRealRefundsByUserId(userId)).thenReturn(BigDecimal.ZERO);
        when(withdrawalRepository.sumActiveWithdrawalsByUserId(userId)).thenReturn(BigDecimal.ZERO);

        BigDecimal withdrawable = walletService.getWithdrawableBalance(userId);

        assertEquals(0, withdrawable.compareTo(BigDecimal.ZERO));
    }

    @Test
    void getWithdrawableBalance_calculatesAccurately_withSePayTopupsAndRefunds() {
        Long userId = 100L;
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setBalance(BigDecimal.valueOf(150000));
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(paymentRepository.sumRealTopupsByUserId(userId)).thenReturn(BigDecimal.valueOf(100000));
        when(refundRepository.sumRealRefundsByUserId(userId)).thenReturn(BigDecimal.valueOf(50000));
        when(withdrawalRepository.sumActiveWithdrawalsByUserId(userId)).thenReturn(BigDecimal.valueOf(20000));

        // Inflow = 150k, with active withdrawals = 20k -> realRemaining = 130k
        // min(walletBalance 150k, realRemaining 130k) = 130k
        BigDecimal withdrawable = walletService.getWithdrawableBalance(userId);

        assertEquals(0, withdrawable.compareTo(BigDecimal.valueOf(130000)));
    }

    @Test
    void withdraw_throwsException_whenAmountBelowMinimum() {
        Long userId = 100L;
        WithdrawRequest request = new WithdrawRequest(
                BigDecimal.valueOf(5000), // Below 10.000
                "MBBank",
                "MB",
                "0123456789",
                "NGUYEN VAN A"
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.withdraw(userId, request));

        assertEquals("WITHDRAW_MIN_AMOUNT", ex.getCode());
    }

    @Test
    void withdraw_throwsException_whenAmountExceedsWithdrawableBalance() {
        Long userId = 100L;
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setBalance(BigDecimal.valueOf(50000));
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(paymentRepository.sumRealTopupsByUserId(userId)).thenReturn(BigDecimal.valueOf(20000));
        when(refundRepository.sumRealRefundsByUserId(userId)).thenReturn(BigDecimal.ZERO);
        when(withdrawalRepository.sumActiveWithdrawalsByUserId(userId)).thenReturn(BigDecimal.ZERO);

        // Withdrawable is 20k, but user requests 50k
        WithdrawRequest request = new WithdrawRequest(
                BigDecimal.valueOf(50000),
                "MBBank",
                "MB",
                "0123456789",
                "NGUYEN VAN A"
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.withdraw(userId, request));

        assertEquals("WALLET_WITHDRAW_NOT_ELIGIBLE", ex.getCode());
    }

    @Test
    void withdraw_success_debitsWalletAndCreatesPendingRecord() {
        Long userId = 100L;
        Wallet wallet = new Wallet();
        wallet.setId(1L);
        wallet.setUserId(userId);
        wallet.setBalance(BigDecimal.valueOf(100000));
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(paymentRepository.sumRealTopupsByUserId(userId)).thenReturn(BigDecimal.valueOf(100000));
        when(refundRepository.sumRealRefundsByUserId(userId)).thenReturn(BigDecimal.ZERO);
        when(withdrawalRepository.sumActiveWithdrawalsByUserId(userId)).thenReturn(BigDecimal.ZERO);

        when(transactionRepository.save(any(WalletTransaction.class))).thenAnswer(i -> {
            WalletTransaction tx = i.getArgument(0);
            tx.setId(10L);
            return tx;
        });

        when(withdrawalRepository.save(any(WithdrawalRecord.class))).thenAnswer(i -> {
            WithdrawalRecord r = i.getArgument(0);
            r.setId(99L);
            return r;
        });

        WithdrawRequest request = new WithdrawRequest(
                BigDecimal.valueOf(50000),
                "MBBank",
                "MB",
                "0123456789",
                "nguyen van a"
        );

        WithdrawResponse response = walletService.withdraw(userId, request);

        assertNotNull(response);
        assertEquals("PENDING", response.status());
        assertEquals("NGUYEN VAN A", response.accountHolderName());
        assertEquals(0, response.amount().compareTo(BigDecimal.valueOf(50000)));
        assertEquals(0, wallet.getBalance().compareTo(BigDecimal.valueOf(50000)));

        ArgumentCaptor<WithdrawalRecord> captor = ArgumentCaptor.forClass(WithdrawalRecord.class);
        verify(withdrawalRepository).save(captor.capture());
        assertEquals("PENDING", captor.getValue().getStatus());
        assertEquals("MB", captor.getValue().getBankCode());
    }

    @Test
    void adminApproveWithdrawal_marksCompleted() {
        Long withdrawalId = 55L;
        Long adminUserId = 1L;
        WithdrawalRecord record = new WithdrawalRecord();
        record.setId(withdrawalId);
        record.setUserId(100L);
        record.setAmount(BigDecimal.valueOf(50000));
        record.setStatus("PENDING");

        Wallet wallet = new Wallet();
        wallet.setUserId(100L);
        wallet.setBalance(BigDecimal.valueOf(50000));

        when(withdrawalRepository.findById(withdrawalId)).thenReturn(Optional.of(record));
        when(withdrawalRepository.save(any(WithdrawalRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(walletRepository.findByUserId(100L)).thenReturn(Optional.of(wallet));

        WithdrawResponse response = walletService.adminApproveWithdrawal(withdrawalId, adminUserId);

        assertEquals("COMPLETED", response.status());
        assertEquals(adminUserId, record.getProcessedByUserId());
        assertNotNull(record.getProcessedAt());
    }

    @Test
    void adminRejectWithdrawal_marksRejectedAndRefundsWallet() {
        Long withdrawalId = 55L;
        Long adminUserId = 1L;
        WithdrawalRecord record = new WithdrawalRecord();
        record.setId(withdrawalId);
        record.setUserId(100L);
        record.setAmount(BigDecimal.valueOf(50000));
        record.setReferenceId("WDR-100-123456");
        record.setStatus("PENDING");

        Wallet wallet = new Wallet();
        wallet.setId(1L);
        wallet.setUserId(100L);
        wallet.setBalance(BigDecimal.valueOf(20000));

        when(withdrawalRepository.findById(withdrawalId)).thenReturn(Optional.of(record));
        when(withdrawalRepository.save(any(WithdrawalRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(walletRepository.findByUserId(100L)).thenReturn(Optional.of(wallet));

        WithdrawResponse response = walletService.adminRejectWithdrawal(withdrawalId, adminUserId, "Sai số tài khoản");

        assertEquals("REJECTED", response.status());
        assertEquals("Sai số tài khoản", record.getRejectionReason());
        // Wallet balance refunded: 20000 + 50000 = 70000
        assertEquals(0, wallet.getBalance().compareTo(BigDecimal.valueOf(70000)));
        verify(transactionRepository).save(any(WalletTransaction.class));
    }
}
