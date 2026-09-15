package com.huynqb.laundrylocker.payment.repository;

import com.huynqb.laundrylocker.payment.model.PaymentRecord;
import com.huynqb.laundrylocker.payment.model.RefundRecord;
import com.huynqb.laundrylocker.payment.model.WalletTransaction;
import com.huynqb.laundrylocker.payment.service.PaymentReportRules;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Locale;

/// Điều kiện lọc dùng chung cho các truy vấn admin/báo cáo của payment-service.
/// Mỗi hàm trả null khi tham số rỗng — Specification.allOf bỏ qua null.
public final class PaymentSpecifications {

    private PaymentSpecifications() {
    }

    // ---- payments ----

    public static Specification<PaymentRecord> paymentStatusIn(Collection<String> statuses) {
        return empty(statuses) ? null : (root, query, cb) -> upper(root.get("status"), cb).in(upperAll(statuses));
    }

    public static Specification<PaymentRecord> paymentMethodIn(Collection<String> methods) {
        return empty(methods) ? null : (root, query, cb) -> upper(root.get("method"), cb).in(upperAll(methods));
    }

    public static Specification<PaymentRecord> paymentUser(Long userId) {
        return userId == null ? null : (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<PaymentRecord> paymentOrder(Long orderId) {
        return orderId == null ? null : (root, query, cb) -> cb.equal(root.get("orderId"), orderId);
    }

    /// kind = ORDER | TOPUP (null/ALL ⇒ không lọc).
    public static Specification<PaymentRecord> paymentKind(String kind) {
        if (kind == null || kind.isBlank() || "ALL".equalsIgnoreCase(kind)) {
            return null;
        }
        if (PaymentReportRules.KIND_TOPUP.equalsIgnoreCase(kind)) {
            return (root, query, cb) -> cb.or(
                    cb.equal(upper(root.get("method"), cb), PaymentReportRules.METHOD_TOPUP),
                    cb.le(root.get("orderId"), 0L));
        }
        return (root, query, cb) -> cb.and(
                cb.notEqual(upper(root.get("method"), cb), PaymentReportRules.METHOD_TOPUP),
                cb.gt(root.get("orderId"), 0L));
    }

    public static Specification<PaymentRecord> paymentCreatedBetween(LocalDateTime from, LocalDateTime toExclusive) {
        return between("createdAt", from, toExclusive);
    }

    /// Thời điểm thu tiền = coalesce(updatedAt, createdAt) của payment COMPLETED.
    public static Specification<PaymentRecord> paymentPaidBetween(LocalDateTime from, LocalDateTime toExclusive) {
        return (root, query, cb) -> {
            Expression<LocalDateTime> paidAt = cb.coalesce(root.<LocalDateTime>get("updatedAt"), root.<LocalDateTime>get("createdAt"));
            return cb.and(
                    cb.equal(upper(root.get("status"), cb), PaymentReportRules.STATUS_COMPLETED),
                    from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(paidAt, from),
                    toExclusive == null ? cb.conjunction() : cb.lessThan(paidAt, toExclusive));
        };
    }

    public static Specification<PaymentRecord> paymentReferenceLike(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> {
            var byReference = cb.or(
                    cb.like(cb.lower(root.get("referenceId")), pattern),
                    cb.like(cb.lower(root.get("referenceTransactionId")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern));
            Long numeric = parseLong(q.trim());
            return numeric == null
                    ? byReference
                    : cb.or(byReference, cb.equal(root.get("id"), numeric), cb.equal(root.get("orderId"), numeric));
        };
    }

    // ---- refunds ----

    public static Specification<RefundRecord> refundStatusIn(Collection<String> statuses) {
        return empty(statuses) ? null : (root, query, cb) -> upper(root.get("status"), cb).in(upperAll(statuses));
    }

    public static Specification<RefundRecord> refundOrder(Long orderId) {
        return orderId == null ? null : (root, query, cb) -> cb.equal(root.get("orderId"), orderId);
    }

    public static Specification<RefundRecord> refundPayment(Long paymentId) {
        return paymentId == null ? null : (root, query, cb) -> cb.equal(root.get("paymentId"), paymentId);
    }

    /// Refund không lưu userId — lọc qua payment gốc.
    public static Specification<RefundRecord> refundUser(Long userId) {
        return userId == null ? null : (root, query, cb) -> {
            Subquery<Long> paymentIds = query.subquery(Long.class);
            Root<PaymentRecord> payment = paymentIds.from(PaymentRecord.class);
            paymentIds.select(payment.get("id")).where(cb.equal(payment.get("userId"), userId));
            return root.get("paymentId").in(paymentIds);
        };
    }

    public static Specification<RefundRecord> refundRequestedBetween(LocalDateTime from, LocalDateTime toExclusive) {
        return between("requestedAt", from, toExclusive);
    }

    /// Refund COMPLETED của đơn thật (orderId &gt; 0), theo coalesce(processedAt, requestedAt).
    public static Specification<RefundRecord> completedOrderRefundBetween(LocalDateTime from, LocalDateTime toExclusive) {
        return (root, query, cb) -> {
            Expression<LocalDateTime> at = cb.coalesce(root.<LocalDateTime>get("processedAt"), root.<LocalDateTime>get("requestedAt"));
            return cb.and(
                    cb.equal(upper(root.get("status"), cb), PaymentReportRules.STATUS_COMPLETED),
                    cb.gt(root.get("orderId"), 0L),
                    from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(at, from),
                    toExclusive == null ? cb.conjunction() : cb.lessThan(at, toExclusive));
        };
    }

    // ---- wallet transactions ----

    public static Specification<WalletTransaction> walletUser(Long userId) {
        return userId == null ? null : (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<WalletTransaction> walletTypeIn(Collection<String> types) {
        return empty(types) ? null : (root, query, cb) -> upper(root.get("type"), cb).in(upperAll(types));
    }

    public static Specification<WalletTransaction> walletSourceIn(Collection<String> sources) {
        return empty(sources) ? null : (root, query, cb) -> upper(root.get("source"), cb).in(upperAll(sources));
    }

    public static Specification<WalletTransaction> walletCreatedBetween(LocalDateTime from, LocalDateTime toExclusive) {
        return between("createdAt", from, toExclusive);
    }

    public static Specification<WalletTransaction> walletReferenceLike(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("referenceId")), pattern),
                cb.like(cb.lower(root.get("description")), pattern));
    }

    // ---- helpers ----

    private static <T> Specification<T> between(String field, LocalDateTime from, LocalDateTime toExclusive) {
        if (from == null && toExclusive == null) {
            return null;
        }
        return (root, query, cb) -> cb.and(
                from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get(field), from),
                toExclusive == null ? cb.conjunction() : cb.lessThan(root.get(field), toExclusive));
    }

    private static Expression<String> upper(jakarta.persistence.criteria.Path<String> path,
                                            jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.upper(path);
    }

    private static Collection<String> upperAll(Collection<String> values) {
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> v.trim().toUpperCase(Locale.ROOT))
                .toList();
    }

    /// Ghép AND các điều kiện, bỏ qua null; không có điều kiện nào ⇒ lấy tất cả.
    @SafeVarargs
    public static <T> Specification<T> all(Specification<T>... specs) {
        Specification<T> result = (root, query, cb) -> cb.conjunction();
        for (Specification<T> spec : specs) {
            if (spec != null) {
                result = result.and(spec);
            }
        }
        return result;
    }

    private static boolean empty(Collection<String> values) {
        return values == null || values.stream().allMatch(v -> v == null || v.isBlank());
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
