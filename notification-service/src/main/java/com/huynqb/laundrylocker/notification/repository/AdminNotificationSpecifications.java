package com.huynqb.laundrylocker.notification.repository;

import com.huynqb.laundrylocker.notification.model.NotificationMessage;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/// Bộ lọc danh sách thông báo ở trang admin.
public final class AdminNotificationSpecifications {

    /// Cụm chữ của thông báo ví cũ (nạp/rút/trừ/hoàn vào ví, "ví voucher" của khuyến mãi cũ), viết thường.
    /// Ví Lock.R đã gỡ khỏi sản phẩm: DB vẫn giữ các thông báo này nhưng trang admin không hiện.
    static final List<String> LEGACY_WALLET_PHRASES = List.of(
            "nạp ví", "vào ví", "từ ví", "ví của bạn", "số dư ví", "ví lockr", "ví lock.r", "ví voucher", "rút tiền");

    private AdminNotificationSpecifications() {
    }

    /// Bỏ thông báo ví cũ: loại `WALLET_*` / `WITHDRAW*` hoặc tiêu đề/nội dung nói về ví.
    public static Specification<NotificationMessage> notLegacyWallet() {
        return (root, query, cb) -> cb.not(legacyWallet(root, cb));
    }

    /// Lọc của bảng thông báo admin; tham số rỗng = không lọc theo tiêu chí đó.
    ///
    /// @param status       `READ` | `UNREAD`
    /// @param type         loại thông báo, khớp đúng
    /// @param search       chữ tìm trong tiêu đề / nội dung, hoặc mã thông báo
    /// @param recipientIds người nhận có tên/email khớp `search` (trang admin tra từ danh sách người dùng)
    public static Specification<NotificationMessage> adminFilter(
            String status, String type, String search, Collection<Long> recipientIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.not(legacyWallet(root, cb)));
            if (StringUtils.hasText(status)) {
                Predicate read = isRead(root, cb);
                predicates.add("READ".equalsIgnoreCase(status.trim()) ? read : cb.not(read));
            }
            if (StringUtils.hasText(type)) {
                predicates.add(cb.equal(root.get("type"), type.trim()));
            }
            if (StringUtils.hasText(search)) {
                String like = "%" + escape(search.trim().toLowerCase(Locale.ROOT)) + "%";
                List<Predicate> any = new ArrayList<>();
                any.add(cb.like(cb.lower(root.get("title")), like, '\\'));
                any.add(cb.like(cb.lower(root.get("message")), like, '\\'));
                Long id = parseId(search);
                if (id != null) {
                    any.add(cb.equal(root.get("id"), id));
                }
                if (recipientIds != null && !recipientIds.isEmpty()) {
                    any.add(root.get("userId").in(recipientIds));
                }
                predicates.add(cb.or(any.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /// Đã đọc: `is_read` hoặc `status = READ` (dữ liệu cũ có thể chỉ ghi một trong hai).
    public static Predicate isRead(Root<NotificationMessage> root, CriteriaBuilder cb) {
        return cb.or(cb.isTrue(root.get("isRead")), cb.equal(cb.upper(root.get("status")), "READ"));
    }

    static Predicate legacyWallet(Root<NotificationMessage> root, CriteriaBuilder cb) {
        Expression<String> type = cb.upper(root.get("type"));
        Expression<String> title = cb.lower(root.get("title"));
        Expression<String> message = cb.lower(root.get("message"));
        List<Predicate> wallet = new ArrayList<>();
        wallet.add(cb.like(type, "WALLET%"));
        wallet.add(cb.like(type, "WITHDRAW%"));
        for (String phrase : LEGACY_WALLET_PHRASES) {
            wallet.add(cb.like(title, "%" + phrase + "%"));
            wallet.add(cb.like(message, "%" + phrase + "%"));
        }
        return cb.or(wallet.toArray(Predicate[]::new));
    }

    private static Long parseId(String search) {
        String text = search.trim().replaceFirst("^#", "");
        if (text.isEmpty() || text.length() > 18 || !text.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return Long.parseLong(text);
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
