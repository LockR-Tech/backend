package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.LockerOrder;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/// Điều kiện lọc cho tìm kiếm đơn phía admin. Hàm trả null khi tham số rỗng.
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<LockerOrder> statusIn(Collection<String> statuses) {
        return in("status", statuses);
    }

    public static Specification<LockerOrder> typeIn(Collection<String> types) {
        return in("type", types);
    }

    public static Specification<LockerOrder> paymentStatusIn(Collection<String> paymentStatuses) {
        return in("paymentStatus", paymentStatuses);
    }

    public static Specification<LockerOrder> createdBetween(LocalDateTime from, LocalDateTime toExclusive) {
        if (from == null && toExclusive == null) {
            return null;
        }
        return (root, query, cb) -> cb.and(
                from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("createdAt"), from),
                toExclusive == null ? cb.conjunction() : cb.lessThan(root.get("createdAt"), toExclusive));
    }

    public static Specification<LockerOrder> user(Long userId) {
        return userId == null ? null : (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    /// Đơn gắn với tủ: tủ gửi/nhận (lockerId) hoặc tủ đích drone.
    public static Specification<LockerOrder> lockerIn(Collection<Long> lockerIds) {
        if (lockerIds == null || lockerIds.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> cb.or(root.get("lockerId").in(lockerIds), root.get("destinationLockerId").in(lockerIds));
    }

    /// Cửa hàng: storeId của đơn hoặc tủ thuộc cửa hàng (đơn SEND/RENTAL không lưu storeId).
    public static Specification<LockerOrder> store(Long storeId, Collection<Long> lockerIdsOfStore) {
        if (storeId == null) {
            return null;
        }
        return (root, query, cb) -> {
            List<Predicate> any = new ArrayList<>();
            any.add(cb.equal(root.get("storeId"), storeId));
            if (lockerIdsOfStore != null && !lockerIdsOfStore.isEmpty()) {
                any.add(root.get("lockerId").in(lockerIdsOfStore));
                any.add(root.get("destinationLockerId").in(lockerIdsOfStore));
            }
            return cb.or(any.toArray(Predicate[]::new));
        };
    }

    /// q: mã đơn, SĐT/tên người nhận (chứa, không phân biệt hoa thường), đúng id đơn,
    /// hoặc khách có đúng SĐT đó (`matchedUserId` do service tra user-service). Không tìm theo PIN.
    public static Specification<LockerOrder> keyword(String q, Long matchedUserId) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String trimmed = q.trim();
        String pattern = "%" + trimmed.toLowerCase(Locale.ROOT) + "%";
        Long numericId = parseLong(trimmed);
        return (root, query, cb) -> {
            List<Predicate> any = new ArrayList<>();
            any.add(cb.like(cb.lower(root.get("orderCode")), pattern));
            any.add(cb.like(cb.lower(root.get("receiverPhone")), pattern));
            any.add(cb.like(cb.lower(root.get("receiverName")), pattern));
            if (numericId != null) {
                any.add(cb.equal(root.get("id"), numericId));
            }
            if (matchedUserId != null) {
                any.add(cb.equal(root.get("userId"), matchedUserId));
            }
            return cb.or(any.toArray(Predicate[]::new));
        };
    }

    @SafeVarargs
    public static Specification<LockerOrder> all(Specification<LockerOrder>... specs) {
        Specification<LockerOrder> result = (root, query, cb) -> cb.conjunction();
        for (Specification<LockerOrder> spec : specs) {
            if (spec != null) {
                result = result.and(spec);
            }
        }
        return result;
    }

    private static Specification<LockerOrder> in(String field, Collection<String> values) {
        List<String> normalized = values == null
                ? List.of()
                : values.stream()
                        .filter(v -> v != null && !v.isBlank())
                        .map(v -> v.trim().toUpperCase(Locale.ROOT))
                        .distinct()
                        .toList();
        if (normalized.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> cb.upper(root.get(field)).in(normalized);
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
