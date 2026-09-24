package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.payment.dto.WalletResponse;
import com.huynqb.laundrylocker.payment.dto.WalletTransactionResponse;
import com.huynqb.laundrylocker.payment.dto.WithdrawRequest;
import com.huynqb.laundrylocker.payment.dto.WithdrawResponse;
import com.huynqb.laundrylocker.payment.dto.admin.OrderBrief;
import com.huynqb.laundrylocker.payment.model.Wallet;
import com.huynqb.laundrylocker.payment.model.WalletTransaction;
import com.huynqb.laundrylocker.payment.model.WithdrawalRecord;
import com.huynqb.laundrylocker.payment.repository.PaymentRepository;
import com.huynqb.laundrylocker.payment.repository.RefundRepository;
import com.huynqb.laundrylocker.payment.repository.WalletRepository;
import com.huynqb.laundrylocker.payment.repository.WalletTransactionRepository;
import com.huynqb.laundrylocker.payment.repository.WithdrawalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WalletService {

    public static final String SOURCE_TOPUP = "TOPUP";
    public static final String SOURCE_ORDER_PAYMENT = "ORDER_PAYMENT";
    public static final String SOURCE_REFUND = "REFUND";
    public static final String SOURCE_ADJUST = "ADJUST";
    public static final String SOURCE_WITHDRAW = "WITHDRAW";

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentReferenceResolver resolver;

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
        List<WalletTransaction> transactions = transactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        // Tra cứu theo lô (một lời gọi Feign cho cả trang) thay vì mỗi giao dịch một lần —
        // cùng cơ chế PaymentReferenceResolver mà trang admin đang dùng, nên "mã đơn hàng"
        // app khách thấy luôn khớp với admin cho cùng một biến động.
        Map<Long, OrderBrief> orders = resolver.orders(
                transactions.stream().map(WalletTransactionRefs::relatedOrderId).toList());
        return transactions.stream().map(tx -> toResponse(tx, orders)).toList();
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

    @Transactional(readOnly = true)
    public BigDecimal getWithdrawableBalance(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId).orElseGet(this::emptyWallet);
        if (wallet.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal sumTopups = paymentRepository.sumRealTopupsByUserId(userId);
        BigDecimal sumRefunds = refundRepository.sumRealRefundsByUserId(userId);
        BigDecimal totalInflow = (sumTopups != null ? sumTopups : BigDecimal.ZERO)
                .add(sumRefunds != null ? sumRefunds : BigDecimal.ZERO);
        BigDecimal totalActiveWithdrawals = withdrawalRepository.sumActiveWithdrawalsByUserId(userId);
        if (totalActiveWithdrawals == null) {
            totalActiveWithdrawals = BigDecimal.ZERO;
        }
        BigDecimal realRemaining = totalInflow.subtract(totalActiveWithdrawals);
        if (realRemaining.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return wallet.getBalance().min(realRemaining);
    }

    @Transactional
    public WithdrawResponse withdraw(Long userId, WithdrawRequest request) {
        requirePositive(request.amount());
        if (request.amount().compareTo(BigDecimal.valueOf(10000)) < 0) {
            throw new BusinessException("WITHDRAW_MIN_AMOUNT", "Số tiền rút tối thiểu là 10.000 VND");
        }
        BigDecimal withdrawable = getWithdrawableBalance(userId);
        if (request.amount().compareTo(withdrawable) > 0) {
            throw new BusinessException(
                    "WALLET_WITHDRAW_NOT_ELIGIBLE",
                    "Số tiền rút (" + request.amount() + " VND) vượt quá số dư hợp lệ từ cổng thanh toán SePay ("
                            + withdrawable + " VND)");
        }

        String refId = "WDR-" + userId + "-" + System.currentTimeMillis();
        String holderUpper = request.accountHolderName().trim().toUpperCase();
        String desc = "Rút tiền về " + request.bankName().trim() + " - STK: " + request.accountNumber().trim()
                + " (" + holderUpper + ") [Chờ duyệt]";

        WalletTransaction tx = debit(userId, request.amount(), SOURCE_WITHDRAW, refId, desc);

        WithdrawalRecord record = new WithdrawalRecord();
        record.setUserId(userId);
        record.setAmount(request.amount());
        record.setBankName(request.bankName().trim());
        record.setBankCode(request.bankCode().trim());
        record.setAccountNumber(request.accountNumber().trim());
        record.setAccountHolderName(holderUpper);
        record.setStatus("PENDING");
        record.setReferenceId(refId);
        if (tx != null) {
            record.setTransactionId(tx.getId());
        }
        WithdrawalRecord saved = withdrawalRepository.save(record);

        BigDecimal newWithdrawable = getWithdrawableBalance(userId);
        BigDecimal balanceAfter = tx != null ? tx.getBalanceAfter() : getBalance(userId).balance();

        return toWithdrawResponse(saved, balanceAfter, newWithdrawable);
    }

    @Transactional
    public WithdrawResponse adminApproveWithdrawal(Long withdrawalId, Long adminUserId) {
        WithdrawalRecord record = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new NotFoundException("WithdrawalRecord", withdrawalId));
        if (!"PENDING".equalsIgnoreCase(record.getStatus())) {
            throw new BusinessException("WITHDRAW_INVALID_STATUS", "Yêu cầu rút tiền không ở trạng thái Chờ xử lý");
        }
        record.setStatus("COMPLETED");
        record.setProcessedByUserId(adminUserId);
        record.setProcessedAt(LocalDateTime.now());
        WithdrawalRecord saved = withdrawalRepository.save(record);

        BigDecimal balance = getBalance(record.getUserId()).balance();
        BigDecimal withdrawable = getWithdrawableBalance(record.getUserId());
        return toWithdrawResponse(saved, balance, withdrawable);
    }

    @Transactional
    public WithdrawResponse adminRejectWithdrawal(Long withdrawalId, Long adminUserId, String reason) {
        WithdrawalRecord record = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new NotFoundException("WithdrawalRecord", withdrawalId));
        if (!"PENDING".equalsIgnoreCase(record.getStatus())) {
            throw new BusinessException("WITHDRAW_INVALID_STATUS", "Yêu cầu rút tiền không ở trạng thái Chờ xử lý");
        }
        record.setStatus("REJECTED");
        record.setRejectionReason(reason);
        record.setProcessedByUserId(adminUserId);
        record.setProcessedAt(LocalDateTime.now());
        WithdrawalRecord saved = withdrawalRepository.save(record);

        String refundDesc = "Hoàn tiền yêu cầu rút " + record.getReferenceId() + " bị từ chối"
                + (reason != null && !reason.isBlank() ? ": " + reason : "");
        credit(record.getUserId(), record.getAmount(), SOURCE_REFUND, "RF-" + record.getReferenceId(), refundDesc);

        BigDecimal balance = getBalance(record.getUserId()).balance();
        BigDecimal withdrawable = getWithdrawableBalance(record.getUserId());
        return toWithdrawResponse(saved, balance, withdrawable);
    }

    @Transactional(readOnly = true)
    public List<WithdrawResponse> listUserWithdrawals(Long userId) {
        BigDecimal withdrawable = getWithdrawableBalance(userId);
        BigDecimal balance = getBalance(userId).balance();
        return withdrawalRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(w -> toWithdrawResponse(w, balance, withdrawable))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WithdrawResponse> listAdminWithdrawals(String status) {
        List<WithdrawalRecord> list = StringUtils.hasText(status)
                ? withdrawalRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase())
                : withdrawalRepository.findAllByOrderByCreatedAtDesc();
        return list.stream()
                .map(w -> toWithdrawResponse(w, null, null))
                .toList();
    }

    private WithdrawResponse toWithdrawResponse(WithdrawalRecord w, BigDecimal balanceAfter, BigDecimal withdrawable) {
        return new WithdrawResponse(
                w.getId(),
                w.getReferenceId(),
                w.getAmount(),
                balanceAfter,
                withdrawable,
                w.getBankName(),
                w.getBankCode(),
                w.getAccountNumber(),
                w.getAccountHolderName(),
                w.getStatus(),
                w.getRejectionReason(),
                w.getCreatedAt(),
                w.getProcessedAt()
        );
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

    private WalletTransactionResponse toResponse(WalletTransaction tx, Map<Long, OrderBrief> orders) {
        Long orderId = WalletTransactionRefs.relatedOrderId(tx);
        OrderBrief order = orderId == null ? null : orders.get(orderId);
        return new WalletTransactionResponse(
                tx.getId(),
                tx.getType(),
                tx.getAmount(),
                tx.getBalanceAfter(),
                tx.getSource(),
                tx.getReferenceId(),
                tx.getDescription(),
                tx.getCreatedAt(),
                orderId,
                order == null ? null : order.orderCode());
    }
}
