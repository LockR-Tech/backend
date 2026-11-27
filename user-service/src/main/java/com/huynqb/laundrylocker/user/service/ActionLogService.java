package com.huynqb.laundrylocker.user.service;

import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.user.model.ActionLogRecord;
import com.huynqb.laundrylocker.user.model.UserProfile;
import com.huynqb.laundrylocker.user.repository.ActionLogRepository;
import com.huynqb.laundrylocker.user.repository.UserProfileRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/// Lưu và tra nhật ký thao tác (F3-G16). Sự kiện đến từ mọi service qua `admin.action.logged`.
@Service
@RequiredArgsConstructor
public class ActionLogService {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_PAGE_SIZE = 200;

    private final ActionLogRepository repository;
    private final UserProfileRepository users;

    /// Một dòng trả cho web: `actorName` null + `actorUserId` null = hệ thống.
    public record ActionLogView(
            Long id,
            LocalDateTime occurredAt,
            String source,
            Long actorUserId,
            String actorName,
            String actorRoles,
            String action,
            String targetType,
            Long targetId,
            String targetLabel,
            Long lockerId,
            String fromValue,
            String toValue,
            String note) {
    }

    @Transactional
    public void store(DomainEvent event) {
        if (event == null || event.payload() == null) {
            return;
        }
        if (event.eventId() != null && repository.existsByEventId(event.eventId())) {
            return; // RabbitMQ gửi lại cùng sự kiện.
        }
        Map<String, Object> payload = event.payload();
        String action = text(payload.get("action"));
        if (action == null) {
            return;
        }
        ActionLogRecord record = new ActionLogRecord();
        record.setEventId(event.eventId());
        // Giờ không kèm múi giờ của backend là giờ UTC (web đổi sang giờ Việt Nam khi hiển thị).
        record.setOccurredAt(event.occurredAt() == null
                ? LocalDateTime.now(ZoneOffset.UTC)
                : LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC));
        record.setSource(event.source());
        record.setAction(action);
        record.setTargetType(text(payload.get("targetType")));
        record.setTargetId(number(payload.get("targetId")));
        record.setTargetLabel(text(payload.get("targetLabel")));
        record.setLockerId(number(payload.get("lockerId")));
        record.setNote(text(payload.get("note")));
        Long actorId = number(payload.get("actorUserId"));
        record.setActorUserId(actorId);
        record.setActorRoles(text(payload.get("actorRoles")));
        record.setActorName(actorId == null ? null : nameOf(actorId));
        String from = text(payload.get("fromValue"));
        String to = text(payload.get("toValue"));
        // Gán / đổi / bỏ KTV: giá trị là mã người dùng ⇒ ghi kèm tên lúc đó để đọc được về sau.
        if (action.contains("_TECHNICIAN_")) {
            from = userLabel(from);
            to = userLabel(to);
        }
        record.setFromValue(from);
        record.setToValue(to);
        repository.save(record);
    }

    @Transactional(readOnly = true)
    public PageResponse<ActionLogView> search(
            Integer page, Integer size, String actions, String targetType, Long targetId, Long lockerId,
            Long actorUserId, LocalDate from, LocalDate to, String q) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        Specification<ActionLogRecord> spec = filter(actions, targetType, targetId, lockerId, actorUserId, from, to, q);
        Page<ActionLogRecord> result = repository.findAll(
                spec, PageRequest.of(p, s, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
        return new PageResponse<>(
                result.getContent().stream().map(ActionLogService::view).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    static Specification<ActionLogRecord> filter(
            String actions, String targetType, Long targetId, Long lockerId, Long actorUserId,
            LocalDate from, LocalDate to, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(actions)) {
                // "LOCKER_" ⇒ mọi hành động bắt đầu bằng LOCKER_; nhiều giá trị cách nhau dấu phẩy.
                List<Predicate> any = new ArrayList<>();
                for (String raw : actions.split(",")) {
                    String action = raw.trim().toUpperCase(Locale.ROOT);
                    if (action.isEmpty()) {
                        continue;
                    }
                    any.add(action.endsWith("_") ? cb.like(root.get("action"), action + "%")
                            : cb.equal(root.get("action"), action));
                }
                if (!any.isEmpty()) {
                    predicates.add(cb.or(any.toArray(Predicate[]::new)));
                }
            }
            if (StringUtils.hasText(targetType)) {
                predicates.add(root.get("targetType").in(Arrays.stream(targetType.split(","))
                        .map(t -> t.trim().toUpperCase(Locale.ROOT)).filter(t -> !t.isEmpty()).toList()));
            }
            if (targetId != null) {
                predicates.add(cb.equal(root.get("targetId"), targetId));
            }
            if (lockerId != null) {
                predicates.add(cb.equal(root.get("lockerId"), lockerId));
            }
            if (actorUserId != null) {
                // 0 = chỉ việc hệ thống tự làm, số âm = chỉ việc do người bấm, số dương = một người.
                predicates.add(actorUserId == 0
                        ? cb.isNull(root.get("actorUserId"))
                        : actorUserId < 0
                        ? cb.isNotNull(root.get("actorUserId"))
                        : cb.equal(root.get("actorUserId"), actorUserId));
            }
            // Ngày lọc là ngày theo giờ Việt Nam; cột lưu giờ UTC.
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), utcStartOf(from)));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"), utcStartOf(to.plusDays(1))));
            }
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("actorName")), like),
                        cb.like(cb.lower(root.get("targetLabel")), like),
                        cb.like(cb.lower(root.get("note")), like),
                        cb.like(cb.lower(root.get("fromValue")), like),
                        cb.like(cb.lower(root.get("toValue")), like),
                        cb.like(cb.lower(root.get("action")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static LocalDateTime utcStartOf(LocalDate businessDay) {
        return LocalDateTime.ofInstant(businessDay.atStartOfDay(BUSINESS_ZONE).toInstant(), ZoneOffset.UTC);
    }

    private static ActionLogView view(ActionLogRecord r) {
        return new ActionLogView(r.getId(), r.getOccurredAt(), r.getSource(), r.getActorUserId(), r.getActorName(),
                r.getActorRoles(), r.getAction(), r.getTargetType(), r.getTargetId(), r.getTargetLabel(),
                r.getLockerId(), r.getFromValue(), r.getToValue(), r.getNote());
    }

    private String nameOf(Long userId) {
        return users.findById(userId).map(ActionLogService::displayName).orElse(null);
    }

    /// "Tên (#id)" cho mã người dùng; giữ nguyên nếu không phải số hoặc không tìm thấy.
    private String userLabel(String value) {
        if (value == null) {
            return null;
        }
        try {
            long id = Long.parseLong(value.trim());
            String name = nameOf(id);
            return name == null ? "#" + id : name + " (#" + id + ")";
        } catch (NumberFormatException ex) {
            return value;
        }
    }

    static String displayName(UserProfile user) {
        String name = (Objects.toString(user.getFirstName(), "") + " " + Objects.toString(user.getLastName(), "")).trim();
        if (!name.isEmpty()) {
            return name;
        }
        return user.getEmail() != null ? user.getEmail() : user.getPhoneNumber();
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Long number(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
