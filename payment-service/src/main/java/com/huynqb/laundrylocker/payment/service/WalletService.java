package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.payment.dto.WalletResponse;
import com.huynqb.laundrylocker.payment.dto.WalletTransactionResponse;
import com.huynqb.laundrylocker.payment.model.Wallet;
import com.huynqb.laundrylocker.payment.model.WalletTransaction;
import com.huynqb.laundrylocker.payment.repository.WalletRepository;
import com.huynqb.laundrylocker.payment.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletService {

    public static final String SOURCE_TOPUP = "TOPUP";
    public static final String SOURCE_ORDER_PAYMENT = "ORDER_PAYMENT";
    public static final String SOURCE_REFUND = "REFUND";
    public static final String SOURCE_ADJUST = "ADJUST";

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    @Transactional
    public Wallet getOrCreate(Long userId) {
        return walletRepository
                .findByUserId(userId)
                .orElseGet(
                        () -> {
                            Wallet wallet = new Wallet();
                            wallet.setUserId(userId);
                            wallet.setBalance(BigDecimal.ZERO);
                            return walletRepository.save(wallet);
                        });
    }

    @Transactional(readOnly = true)
    public WalletResponse getBalance(Long userId) {
        Wallet wallet =
                walletRepository.findByUserId(userId).orElseGet(this::emptyWallet);
        return new WalletResponse(userId, wallet.getBalance(), wallet.getCurrency());
    }

    @Transactional(readOnly = true)
    public List<WalletTransactionResponse> history(Long userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Add money to a wallet. Idempotent per (source, referenceId). Returns null if already applied.
     */
    @Transactional
    public WalletTransaction credit(
            Long userId, BigDecimal amount, String source, String referenceId, String description) {
        requirePositive(amount);
        if (referenceId != null && transactionRepository.existsBySourceAndReferenceId(source, referenceId)) {
            return null; // already credited (e.g. duplicate VNPay callback)
        }
        Wallet wallet = getOrCreate(userId);
        BigDecimal newBalance = wallet.getBalance().add(amount);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);
        return transactionRepository.save(
                record(wallet, userId, "CREDIT", amount, newBalance, source, referenceId, description));
    }

    /**
     * Deduct money from a wallet. Throws WALLET_INSUFFICIENT when balance is too low.
     */
    @Transactional
    public WalletTransaction debit(
            Long userId, BigDecimal amount, String source, String referenceId, String description) {
        requirePositive(amount);
        if (referenceId != null && transactionRepository.existsBySourceAndReferenceId(source, referenceId)) {
            return null; // already debited
        }
        Wallet wallet = getOrCreate(userId);
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BusinessException("WALLET_INSUFFICIENT", "Số dư ví không đủ");
        }
        BigDecimal newBalance = wallet.getBalance().subtract(amount);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);
        return transactionRepository.save(
                record(wallet, userId, "DEBIT", amount, newBalance, source, referenceId, description));
    }

    /**
     * Admin manual adjustment: positive credits, negative debits (never below zero).
     */
    @Transactional
    public WalletResponse adjust(Long userId, BigDecimal amount, String reason) {
        if (amount.signum() == 0) {
            throw new BusinessException("WALLET_ADJUST_INVALID", "Số tiền điều chỉnh phải khác 0");
        }
        String description = "Admin điều chỉnh" + (reason == null ? "" : ": " + reason);
        if (amount.signum() > 0) {
            credit(userId, amount, SOURCE_ADJUST, null, description);
        } else {
            debit(userId, amount.abs(), SOURCE_ADJUST, null, description);
        }
        return getBalance(userId);
    }

    private WalletTransaction record(
            Wallet wallet,
            Long userId,
            String type,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String source,
            String referenceId,
            String description) {
        WalletTransaction tx = new WalletTransaction();
        tx.setWalletId(wallet.getId());
        tx.setUserId(userId);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setBalanceAfter(balanceAfter);
        tx.setSource(source);
        tx.setReferenceId(referenceId);
        tx.setDescription(description);
        return tx;
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("WALLET_AMOUNT_INVALID", "Số tiền phải lớn hơn 0");
        }
    }

    private Wallet emptyWallet() {
        Wallet wallet = new Wallet();
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setCurrency("VND");
        return wallet;
    }

    private WalletTransactionResponse toResponse(WalletTransaction tx) {
        return new WalletTransactionResponse(
                tx.getId(),
                tx.getType(),
                tx.getAmount(),
                tx.getBalanceAfter(),
                tx.getSource(),
                tx.getReferenceId(),
                tx.getDescription(),
                tx.getCreatedAt());
    }
}
