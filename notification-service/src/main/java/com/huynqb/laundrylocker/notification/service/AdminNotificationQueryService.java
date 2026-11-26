package com.huynqb.laundrylocker.notification.service;

import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.util.DeleteReason;
import com.huynqb.laundrylocker.notification.dto.AdminNotificationBulkDeleteRequest;
import com.huynqb.laundrylocker.notification.dto.AdminNotificationStats;
import com.huynqb.laundrylocker.notification.dto.NotificationResponse;
import com.huynqb.laundrylocker.notification.model.NotificationMessage;
import com.huynqb.laundrylocker.notification.repository.AdminNotificationSpecifications;
import com.huynqb.laundrylocker.notification.repository.NotificationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/// Trang Thông báo admin: lọc + phân trang ở DB (bảng có hàng chục nghìn dòng), số liệu tổng, xoá mềm hàng
/// loạt bằng một lệnh. Mọi truy vấn bỏ thông báo ví cũ ([AdminNotificationSpecifications#notLegacyWallet]).
@Service
@RequiredArgsConstructor
public class AdminNotificationQueryService {

    static final int MAX_PAGE_SIZE = 100;
    private static final int DELETE_CHUNK = 500;

    private final NotificationRepository notificationRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> search(
            int page, int size, String status, String type, String search, Collection<Long> recipientIds) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<NotificationMessage> result = notificationRepository.findAll(
                AdminNotificationSpecifications.adminFilter(status, type, search, recipientIds), pageable);
        return new PageResponse<>(
                result.getContent().stream().map(NotificationResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminNotificationStats stats() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Tuple> byType = cb.createTupleQuery();
        Root<NotificationMessage> root = byType.from(NotificationMessage.class);
        Expression<Integer> readFlag = cb.<Integer>selectCase()
                .when(AdminNotificationSpecifications.isRead(root, cb), 1)
                .otherwise(0);
        byType.multiselect(root.get("type"), cb.count(root), cb.sum(readFlag))
                .where(AdminNotificationSpecifications.notLegacyWallet().toPredicate(root, byType, cb))
                .groupBy(root.get("type"));
        long total = 0;
        long read = 0;
        List<AdminNotificationStats.TypeCount> types = new ArrayList<>();
        for (Tuple row : entityManager.createQuery(byType).getResultList()) {
            long count = number(row.get(1));
            total += count;
            read += number(row.get(2));
            types.add(new AdminNotificationStats.TypeCount(row.get(0, String.class), count));
        }
        types.sort(Comparator.comparingLong(AdminNotificationStats.TypeCount::count).reversed()
                .thenComparing(AdminNotificationStats.TypeCount::type, Comparator.nullsLast(Comparator.naturalOrder())));

        CriteriaQuery<Tuple> readTimes = cb.createTupleQuery();
        Root<NotificationMessage> readRoot = readTimes.from(NotificationMessage.class);
        readTimes.multiselect(readRoot.get("createdAt"), readRoot.get("readAt"))
                .where(cb.and(
                        AdminNotificationSpecifications.notLegacyWallet().toPredicate(readRoot, readTimes, cb),
                        cb.isNotNull(readRoot.get("readAt"))));
        long minutesSum = 0;
        long readCount = 0;
        for (Tuple row : entityManager.createQuery(readTimes).getResultList()) {
            LocalDateTime created = row.get(0, LocalDateTime.class);
            LocalDateTime readAt = row.get(1, LocalDateTime.class);
            if (created != null && readAt != null && !readAt.isBefore(created)) {
                minutesSum += Duration.between(created, readAt).toMinutes();
                readCount++;
            }
        }
        Long averageReadMinutes = readCount == 0 ? null : Math.round((double) minutesSum / readCount);
        return new AdminNotificationStats(total, total - read, read, averageReadMinutes, types);
    }

    /// Xoá mềm theo `ids` hoặc mọi thông báo khớp bộ lọc (`all`); trả mã các thông báo đã xoá.
    @Transactional
    public List<Long> bulkDelete(AdminNotificationBulkDeleteRequest request, Long actorUserId) {
        String reason = DeleteReason.require(request == null ? null : request.reason());
        Specification<NotificationMessage> spec;
        if (Boolean.TRUE.equals(request.all())) {
            spec = AdminNotificationSpecifications.adminFilter(
                    request.status(), request.type(), request.search(), request.recipientIds());
        } else {
            List<Long> ids = request.ids() == null ? List.of()
                    : request.ids().stream().filter(Objects::nonNull).distinct().toList();
            if (ids.isEmpty()) {
                throw new BusinessException("NOTIFICATION_IDS_REQUIRED", "Chưa chọn thông báo nào để xoá");
            }
            spec = AdminNotificationSpecifications.notLegacyWallet()
                    .and((root, query, cb) -> root.get("id").in(ids));
        }
        List<Long> matched = ids(spec);
        LocalDateTime now = LocalDateTime.now();
        for (int from = 0; from < matched.size(); from += DELETE_CHUNK) {
            List<Long> chunk = matched.subList(from, Math.min(from + DELETE_CHUNK, matched.size()));
            notificationRepository.softDelete(chunk, now, actorUserId, reason);
        }
        return matched;
    }

    private List<Long> ids(Specification<NotificationMessage> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<NotificationMessage> root = query.from(NotificationMessage.class);
        query.select(root.get("id")).where(spec.toPredicate(root, query, cb)).orderBy(cb.asc(root.get("id")));
        return entityManager.createQuery(query).getResultList();
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
