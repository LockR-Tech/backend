package com.huynqb.laundrylocker.payment.service;

import com.huynqb.laundrylocker.payment.dto.TransactionMethodTotalResponse;
import com.huynqb.laundrylocker.payment.dto.UserSpendingStatsResponse;
import com.huynqb.laundrylocker.payment.dto.WalletTransactionResponse;
import com.huynqb.laundrylocker.payment.dto.admin.OrderBrief;
import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import com.huynqb.laundrylocker.payment.model.WalletTransaction;
import com.huynqb.laundrylocker.payment.repository.PaymentRepository;
import com.huynqb.laundrylocker.payment.repository.WalletRepository;
import com.huynqb.laundrylocker.payment.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Lịch sử giao dịch của khách (mục "Lịch sử giao dịch" trên app, `GET /api/wallet/transactions` …): các khoản
/// đã trả cho đơn (SePay, tiền mặt…) cùng biến động cũ của ví. Ví Lock.R đã gỡ khỏi sản phẩm — không còn nạp,
/// rút, trừ hay cộng ví; lớp này chỉ đọc dữ liệu cũ để lịch sử của khách không bị mất.
@Service
@RequiredArgsConstructor
public class TransactionHistoryService {

    /// Nguồn ghi ở `wallet_transactions` (dữ liệu cũ) — dùng để phân loại dòng lịch sử.
    static final String SOURCE_TOPUP = "TOPUP";
    static final String SOURCE_WITHDRAW = "WITHDRAW";

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentReferenceResolver resolver;

    @Transactional(readOnly = true)
    public List<WalletTransactionResponse> history(Long userId) {
        BigDecimal balance = walletRepository.findByUserId(userId)
                .map(wallet -> wallet.getBalance() == null ? BigDecimal.ZERO : wallet.getBalance())
                .orElse(BigDecimal.ZERO);
        List<WalletTransaction> transactions = transactionRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // Khoản trả trực tiếp cho đơn (SePay, tiền mặt; dữ liệu cũ còn VNPay/MoMo). Khoản trả bằng ví cũ đã có
        // dòng tương ứng trong `wallet_transactions` nên bỏ qua để không đếm hai lần.
        List<PaymentRecord> directPayments = paymentRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(p -> "COMPLETED".equalsIgnoreCase(p.getStatus()))
                .filter(p -> p.getMethod() != null && !"WALLET".equalsIgnoreCase(p.getMethod()))
                .filter(p -> p.getOrderId() != null && p.getOrderId() > 0)
                .toList();

        List<Long> orderIds = new ArrayList<>();
        orderIds.addAll(transactions.stream().map(WalletTransactionRefs::relatedOrderId).filter(Objects::nonNull).toList());
        orderIds.addAll(directPayments.stream().map(PaymentRecord::getOrderId).filter(Objects::nonNull).toList());
        Map<Long, OrderBrief> orders = resolver.orders(orderIds);

        List<WalletTransactionResponse> result = new ArrayList<>();
        for (WalletTransaction tx : transactions) {
            result.add(toResponse(tx, orders));
        }
        for (PaymentRecord p : directPayments) {
            OrderBrief order = orders.get(p.getOrderId());
            String orderCode = order != null ? order.orderCode() : null;
            String desc = p.getDescription();
            if (!StringUtils.hasText(desc)) {
                desc = "Thanh toán đơn " + (orderCode != null ? orderCode : ("#" + p.getOrderId())) + " qua " + p.getMethod();
            }
            String refId = StringUtils.hasText(p.getReferenceTransactionId()) ? p.getReferenceTransactionId() : p.getReferenceId();
            result.add(new WalletTransactionResponse(
                    -p.getId(), "DEBIT", p.getAmount(), balance, p.getMethod(), refId, desc, p.getCreatedAt(),
                    p.getOrderId(), orderCode));
        }

        result.sort((a, b) -> {
            if (a.createdAt() == null || b.createdAt() == null) {
                return 0;
            }
            return b.createdAt().compareTo(a.createdAt());
        });
        return result;
    }

    public static String detectMethod(WalletTransactionResponse tx) {
        String src = tx.source() != null ? tx.source().toUpperCase() : "";
        String ref = tx.referenceId() != null ? tx.referenceId().toUpperCase() : "";
        String desc = tx.description() != null ? tx.description().toUpperCase() : "";

        if (src.contains("SEPAY") || ref.contains("SEPAY") || desc.contains("SEPAY") || ref.startsWith("FT")) {
            return "SEPAY";
        }
        // VNPay/MoMo đã ngừng hỗ trợ; giữ nhận diện để lịch sử cũ vẫn xếp đúng phương thức.
        if (src.contains("VNPAY") || ref.contains("VNPAY") || desc.contains("VNPAY")) {
            return "VNPAY";
        }
        if (src.contains("MOMO") || ref.contains("MOMO") || desc.contains("MOMO")) {
            return "MOMO";
        }
        if (SOURCE_WITHDRAW.equalsIgnoreCase(src) || ref.contains("WDR") || desc.contains("RÚT TIỀN") || desc.contains("RUT TIEN")) {
            return "WITHDRAW";
        }
        if (SOURCE_TOPUP.equalsIgnoreCase(src)) {
            return "SEPAY";
        }
        if (src.contains("CASH") || desc.contains("TIỀN MẶT") || desc.contains("TIEN MAT")) {
            return "CASH";
        }
        return "WALLET";
    }

    public static String getMethodLabel(String method) {
        if (method == null) {
            return "Khác";
        }
        return switch (method.toUpperCase()) {
            case "SEPAY" -> "Chuyển khoản SePay";
            case "CASH" -> "Tiền mặt";
            // Chỉ còn ở giao dịch cũ (đã ngừng hỗ trợ).
            case "VNPAY" -> "Cổng VNPay";
            case "MOMO" -> "MoMo";
            case "WALLET", "WITHDRAW" -> "Phương thức cũ";
            default -> "Khác";
        };
    }

    @Transactional(readOnly = true)
    public TransactionMethodTotalResponse calculateTotalByMethod(
            Long userId, String methodStr, String periodStr, String fromStr, String toStr) {
        List<WalletTransactionResponse> allTx = history(userId);
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime filterStart = null;
        LocalDateTime filterEnd = null;
        if (StringUtils.hasText(fromStr)) {
            try {
                filterStart = LocalDate.parse(fromStr.trim()).atStartOfDay();
            } catch (Exception ignored) {
                // ngày sai định dạng ⇒ coi như không lọc
            }
        }
        if (StringUtils.hasText(toStr)) {
            try {
                filterEnd = LocalDate.parse(toStr.trim()).plusDays(1).atStartOfDay();
            } catch (Exception ignored) {
                // ngày sai định dạng ⇒ coi như không lọc
            }
        }
        if (filterStart == null && filterEnd == null) {
            filterStart = periodStart(periodStr, now);
        }

        final LocalDateTime start = filterStart;
        final LocalDateTime end = filterEnd;
        List<WalletTransactionResponse> filtered = allTx.stream().filter(t -> {
            if (t.createdAt() == null) {
                return false;
            }
            if (start != null && t.createdAt().isBefore(start)) {
                return false;
            }
            return end == null || t.createdAt().isBefore(end);
        }).toList();

        // Phương thức đang hỗ trợ luôn có mặt (kể cả 0đ); phương thức cũ chỉ xuất hiện khi có dữ liệu.
        Map<String, BigDecimal> summary = new LinkedHashMap<>();
        summary.put("SEPAY", BigDecimal.ZERO);
        Map<String, BigDecimal> expenseByMethod = new LinkedHashMap<>();
        Map<String, BigDecimal> incomeByMethod = new LinkedHashMap<>();
        Map<String, Integer> countByMethod = new LinkedHashMap<>();
        for (String m : summary.keySet()) {
            expenseByMethod.put(m, BigDecimal.ZERO);
            incomeByMethod.put(m, BigDecimal.ZERO);
            countByMethod.put(m, 0);
        }

        BigDecimal allTotal = BigDecimal.ZERO;
        BigDecimal allExpense = BigDecimal.ZERO;
        BigDecimal allIncome = BigDecimal.ZERO;
        for (WalletTransactionResponse tx : filtered) {
            BigDecimal amt = tx.amount() != null ? tx.amount() : BigDecimal.ZERO;
            boolean isCredit = "CREDIT".equalsIgnoreCase(tx.type()) || "TOP_UP".equalsIgnoreCase(tx.type());
            String m = detectMethod(tx);
            summary.merge(m, amt, BigDecimal::add);
            countByMethod.merge(m, 1, Integer::sum);
            if (isCredit) {
                incomeByMethod.merge(m, amt, BigDecimal::add);
                allIncome = allIncome.add(amt);
            } else {
                expenseByMethod.merge(m, amt, BigDecimal::add);
                allExpense = allExpense.add(amt);
            }
            allTotal = allTotal.add(amt);
        }

        String target = StringUtils.hasText(methodStr) ? methodStr.trim().toUpperCase() : "ALL";
        boolean isAll = "ALL".equals(target);
        BigDecimal selectedTotal = isAll ? allTotal : summary.getOrDefault(target, BigDecimal.ZERO);
        BigDecimal selectedExpense = isAll ? allExpense : expenseByMethod.getOrDefault(target, BigDecimal.ZERO);
        BigDecimal selectedIncome = isAll ? allIncome : incomeByMethod.getOrDefault(target, BigDecimal.ZERO);
        int selectedCount = isAll ? filtered.size() : countByMethod.getOrDefault(target, 0);

        List<TransactionMethodTotalResponse.MethodTotalItem> methodItems = summary.entrySet().stream()
                .map(e -> new TransactionMethodTotalResponse.MethodTotalItem(
                        e.getKey(),
                        getMethodLabel(e.getKey()),
                        e.getValue(),
                        expenseByMethod.getOrDefault(e.getKey(), BigDecimal.ZERO),
                        incomeByMethod.getOrDefault(e.getKey(), BigDecimal.ZERO),
                        countByMethod.getOrDefault(e.getKey(), 0)))
                .toList();

        return new TransactionMethodTotalResponse(
                periodStr != null ? periodStr.toUpperCase() : "ALL",
                target, selectedTotal, selectedExpense, selectedIncome, selectedCount, summary, methodItems);
    }

    @Transactional(readOnly = true)
    public UserSpendingStatsResponse spendingStats(Long userId, String periodStr) {
        List<WalletTransactionResponse> allTx = history(userId);
        LocalDateTime filterStart = periodStart(periodStr, LocalDateTime.now());
        List<WalletTransactionResponse> filtered = filterStart == null ? allTx
                : allTx.stream().filter(t -> t.createdAt() != null && !t.createdAt().isBefore(filterStart)).toList();

        BigDecimal totalExpense = BigDecimal.ZERO;
        BigDecimal totalIncome = BigDecimal.ZERO;
        Map<String, BigDecimal> byService = new LinkedHashMap<>();
        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();
        byService.put("RENTAL", BigDecimal.ZERO);
        byService.put("SEND", BigDecimal.ZERO);
        byService.put("OTHER", BigDecimal.ZERO);
        byMethod.put("SEPAY", BigDecimal.ZERO);

        for (WalletTransactionResponse tx : filtered) {
            BigDecimal amt = tx.amount() != null ? tx.amount() : BigDecimal.ZERO;
            boolean isCredit = "CREDIT".equalsIgnoreCase(tx.type()) || "TOP_UP".equalsIgnoreCase(tx.type());
            if (isCredit) {
                totalIncome = totalIncome.add(amt);
            } else {
                totalExpense = totalExpense.add(amt);
            }
            byMethod.merge(detectMethod(tx), amt, BigDecimal::add);
            byService.merge(serviceOf(tx, isCredit), amt, BigDecimal::add);
        }

        return new UserSpendingStatsResponse(
                periodStr != null ? periodStr.toUpperCase() : "ALL",
                totalExpense, totalIncome, totalIncome.subtract(totalExpense), filtered.size(), byService, byMethod);
    }

    /// Nhóm dịch vụ của một dòng lịch sử; nạp/rút ví chỉ còn ở dữ liệu cũ.
    private static String serviceOf(WalletTransactionResponse tx, boolean isCredit) {
        String src = tx.source() != null ? tx.source().toUpperCase() : "";
        if (SOURCE_WITHDRAW.equalsIgnoreCase(src)) {
            return "WITHDRAW";
        }
        if (SOURCE_TOPUP.equalsIgnoreCase(src) || isCredit) {
            return "TOPUP";
        }
        String desc = tx.description() != null ? tx.description().toUpperCase() : "";
        String oc = tx.orderCode() != null ? tx.orderCode().toUpperCase() : "";
        if (oc.contains("SND") || oc.contains("DRN") || desc.contains("GỬI") || desc.contains("SEND") || desc.contains("HÀNG")) {
            return "SEND";
        }
        if (oc.contains("LND") || desc.contains("GIẶT")) {
            return "LAUNDRY";
        }
        return "RENTAL";
    }

    private static LocalDateTime periodStart(String periodStr, LocalDateTime now) {
        return switch (periodStr != null ? periodStr.toUpperCase() : "ALL") {
            case "DAY" -> now.toLocalDate().atStartOfDay();
            case "MONTH" -> now.toLocalDate().withDayOfMonth(1).atStartOfDay();
            case "YEAR" -> now.toLocalDate().withDayOfYear(1).atStartOfDay();
            default -> null;
        };
    }

    private WalletTransactionResponse toResponse(WalletTransaction tx, Map<Long, OrderBrief> orders) {
        Long orderId = WalletTransactionRefs.relatedOrderId(tx);
        OrderBrief order = orderId == null ? null : orders.get(orderId);
        return new WalletTransactionResponse(
                tx.getId(), tx.getType(), tx.getAmount(), tx.getBalanceAfter(), tx.getSource(), tx.getReferenceId(),
                tx.getDescription(), tx.getCreatedAt(), orderId, order == null ? null : order.orderCode());
    }
}
