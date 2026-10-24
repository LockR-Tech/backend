package com.huynqb.laundrylocker.iot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.huynqb.laundrylocker.iot.dto.CabinetControlLogResponse;
import com.huynqb.laundrylocker.iot.model.CabinetControlLog;
import com.huynqb.laundrylocker.iot.repository.CabinetControlLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/// Nhật ký điều khiển tủ (trang admin "Điều khiển tủ"). Hai nguồn:
/// - `WEB`: lệnh admin/KTV gửi từ web, ghi ngay khi có kết quả — kể cả `TIMEOUT` lúc tủ mất mạng.
/// - `CABINET`: việc tủ thực sự làm (kích relay, chạy nắp) qua `cabinet/{lockerId}/event/control`.
///   Pi ghi ra thẻ nhớ trước rồi gửi khi có mạng ⇒ có thể tới muộn hoặc lặp; `eventId` chống trùng.
@Slf4j
@Service
@RequiredArgsConstructor
public class CabinetControlLogService {

    static final int DEFAULT_LIMIT = 100;
    static final int MAX_LIMIT = 200;
    private static final Pattern CODE = Pattern.compile("[A-Z_]{1,20}");
    private static final Pattern ACTION = Pattern.compile("[A-Z_]{1,30}");
    /// Đồng hồ Pi lệch quá mức này về tương lai thì lấy giờ nhận.
    private static final long MAX_CLOCK_SKEW_MINUTES = 5;

    private final CabinetControlLogRepository repository;

    public void recordWeb(Long lockerId, Long actorUserId, String action, Integer axis, Long boxId,
                          Double revolutions, String status, String message) {
        try {
            CabinetControlLog entry = new CabinetControlLog();
            entry.setLockerId(lockerId);
            entry.setEventId("web-" + UUID.randomUUID());
            entry.setSource(CabinetControlLog.SOURCE_WEB);
            entry.setActorUserId(actorUserId);
            entry.setAction(action);
            entry.setAxis(axis);
            entry.setBoxId(boxId);
            entry.setRevolutions(revolutions);
            entry.setStatus(status);
            entry.setMessage(truncate(message));
            repository.save(entry);
        } catch (Exception ex) {
            // Ghi nhật ký hỏng không được làm hỏng lệnh điều khiển đã gửi.
            log.warn("Could not record cabinet control log for locker {}: {}", lockerId, ex.getMessage());
        }
    }

    /// `cabinet/{lockerId}/event/control` — trả false khi bỏ qua (thiếu trường, sai định dạng, trùng).
    public boolean recordFromCabinet(Long lockerId, JsonNode data) {
        String eventId = text(data, "eventId");
        String action = upper(text(data, "action"));
        String status = upper(text(data, "status"));
        if (lockerId == null || eventId == null || eventId.length() > 64
                || action == null || !ACTION.matcher(action).matches()
                || status == null || !CODE.matcher(status).matches()) {
            log.warn("Cabinet control event from locker {} ignored: missing or invalid fields", lockerId);
            return false;
        }
        if (repository.existsByEventId(eventId)) {
            return false;
        }
        CabinetControlLog entry = new CabinetControlLog();
        entry.setLockerId(lockerId);
        entry.setEventId(eventId);
        entry.setSource(CabinetControlLog.SOURCE_CABINET);
        entry.setAction(action);
        entry.setStatus(status);
        entry.setAxis(data.hasNonNull("axis") && data.get("axis").canConvertToInt() ? data.get("axis").asInt() : null);
        entry.setBoxId(data.hasNonNull("boxId") && data.get("boxId").canConvertToLong() ? data.get("boxId").asLong() : null);
        if (data.hasNonNull("slotIndex") && data.get("slotIndex").canConvertToInt()) {
            entry.setBoxNumber(data.get("slotIndex").asInt() + 1);
        }
        entry.setRevolutions(data.hasNonNull("revolutions") && data.get("revolutions").isNumber()
                ? data.get("revolutions").asDouble() : null);
        entry.setMessage(truncate(text(data, "message")));
        entry.setOccurredAt(occurredAt(text(data, "occurredAt")));
        try {
            repository.save(entry);
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;   // cùng eventId tới hai lần gần như đồng thời
        }
    }

    public List<CabinetControlLogResponse> recent(Long lockerId, Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        return repository.findByLockerIdOrderByOccurredAtDescIdDesc(lockerId, PageRequest.of(0, size)).stream()
                .map(l -> new CabinetControlLogResponse(l.getId(), l.getLockerId(), l.getSource(), l.getActorUserId(),
                        l.getAction(), l.getAxis(), l.getBoxId(), l.getBoxNumber(), l.getRevolutions(),
                        l.getStatus(), l.getMessage(), l.getOccurredAt(), l.getCreatedAt()))
                .toList();
    }

    private static LocalDateTime occurredAt(String iso) {
        LocalDateTime now = LocalDateTime.now();
        if (iso == null) {
            return now;
        }
        try {
            LocalDateTime at = OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            return at.isAfter(now.plusMinutes(MAX_CLOCK_SKEW_MINUTES)) ? now : at;
        } catch (DateTimeParseException ex) {
            return now;
        }
    }

    private static String text(JsonNode data, String field) {
        return data != null && data.hasNonNull(field) ? data.get(field).asText() : null;
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }
}
