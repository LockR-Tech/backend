package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.dto.DroneFlightControlViews;
import com.huynqb.laundrylocker.order.dto.DroneWeatherAssessment;
import com.huynqb.laundrylocker.order.model.DroneFlightBlock;
import com.huynqb.laundrylocker.order.model.DroneStationSuspension;
import com.huynqb.laundrylocker.order.model.DroneStationSuspensionLog;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneFlightBlockRepository;
import com.huynqb.laundrylocker.order.repository.DroneStationSuspensionLogRepository;
import com.huynqb.laundrylocker.order.repository.DroneStationSuspensionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Điều khiển bay theo trạm và lịch sử chặn bay.
 *
 * <p>Trạm bay = tủ có bãi đáp. Trạm đang dừng thì không làm tủ gửi (bộ chọn tủ gửi bỏ qua, đơn chưa gán drone
 * được đổi sang trạm khác), không nhận drone tới (đặt đơn vào tủ đó bị từ chối), đội bay không nhận đơn và không
 * phóng được qua trạm đó. Tắt bay cả hệ thống vẫn là quy tắc `app.order.drone-flights-suspended`.
 *
 * <p>Mỗi lần hệ thống từ chối bay vì thời tiết hoặc lệnh dừng được ghi vào {@code drone_flight_blocks} bằng giao
 * dịch riêng: lời gọi đặt đơn / phóng drone ném lỗi rồi rollback thì bản ghi vẫn còn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DroneFlightControl {

    public static final Map<String, String> SUSPENSION_REASONS = Map.of(
            "WEATHER", "Thời tiết tại trạm",
            "LANDING_PAD", "Bảo trì bãi đáp",
            "AIRSPACE", "Lệnh cấm bay khu vực",
            "OPERATIONAL", "Lý do vận hành",
            "OTHER", "Lý do khác");

    private static final int MAX_PAGE_SIZE = 100;
    /// Giờ trên server là LocalDateTime theo múi giờ JVM (container chạy UTC); câu báo cho khách theo giờ Việt Nam.
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter RESUME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    private final DroneStationSuspensionRepository suspensions;
    private final DroneStationSuspensionLogRepository suspensionLogs;
    private final DroneFlightBlockRepository blocks;
    private final PlatformTransactionManager transactionManager;

    /// Đơn, người thao tác và tuyến của một lần kiểm tra.
    public record Context(Long orderId, String orderCode, Long actorUserId, Long sourceLockerId, Long destinationLockerId) {

        public static Context of(LockerOrder order, Long actorUserId) {
            Long destination = order.getDestinationLockerId() != null ? order.getDestinationLockerId() : order.getLockerId();
            return new Context(order.getId(), order.getOrderCode(), actorUserId, order.getSourceLockerId(), destination);
        }
    }

    // ------------------------------------------------------------------ trạm

    @Transactional(readOnly = true)
    public Optional<DroneStationSuspension> activeSuspension(Long lockerId) {
        if (lockerId == null) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        return suspensions.findById(lockerId).filter(suspension -> suspension.activeAt(now));
    }

    public boolean suspended(Long lockerId) {
        return activeSuspension(lockerId).isPresent();
    }

    /// Các trạm đang dừng, theo id tủ.
    @Transactional(readOnly = true)
    public Map<Long, DroneStationSuspension> activeSuspensions() {
        LocalDateTime now = LocalDateTime.now();
        return suspensions.findAll().stream()
                .filter(suspension -> suspension.activeAt(now))
                .collect(Collectors.toMap(DroneStationSuspension::getLockerId, Function.identity()));
    }

    /// Trạm đầu tiên trong `lockerIds` đang dừng ⇒ ghi lịch sử (trừ khi `action` null — xem trước báo giá) và từ chối.
    public void assertStationsOpen(String action, Context context, Long... lockerIds) {
        for (Long lockerId : Arrays.stream(lockerIds).filter(Objects::nonNull).distinct().toList()) {
            DroneStationSuspension suspension = activeSuspension(lockerId).orElse(null);
            if (suspension != null) {
                String message = stationMessage(lockerId, context, suspension);
                if (action != null) {
                    record(action, DroneFlightBlock.STATION_SUSPENDED, context, lockerId, message, null);
                }
                throw new BusinessException("DRONE_STATION_SUSPENDED", message);
            }
        }
    }

    static String stationMessage(Long lockerId, Context context, DroneStationSuspension suspension) {
        String role = Objects.equals(lockerId, context == null ? null : context.destinationLockerId())
                ? "Tủ nhận" : Objects.equals(lockerId, context == null ? null : context.sourceLockerId())
                        ? "Trạm cất cánh" : "Trạm bay #" + lockerId;
        String label = reasonLabel(suspension.getReason());
        return role + " đang tạm dừng bay (" + label + ")"
                + (suspension.getResumeAt() == null ? ""
                        : ", dự kiến mở lại lúc " + suspension.getResumeAt()
                                .atZone(ZoneId.systemDefault()).withZoneSameInstant(VIETNAM).format(RESUME_FORMAT))
                + ".";
    }

    @Transactional
    public DroneFlightControlViews.StationSuspension suspend(
            Long lockerId, DroneFlightControlViews.SuspendStationRequest request, Long actorUserId) {
        if (lockerId == null) {
            throw new BusinessException("DRONE_STATION_REQUIRED", "Station locker id is required");
        }
        String reason = request == null || !StringUtils.hasText(request.reason())
                ? "OTHER" : request.reason().trim().toUpperCase();
        if (!SUSPENSION_REASONS.containsKey(reason)) {
            throw new BusinessException("DRONE_STATION_SUSPEND_REASON_INVALID",
                    "Lý do dừng trạm phải là một trong " + SUSPENSION_REASONS.keySet());
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime resumeAt = request == null ? null : request.resumeAt();
        if (resumeAt != null && !resumeAt.isAfter(now)) {
            throw new BusinessException("DRONE_STATION_RESUME_AT_INVALID", "Giờ mở lại phải sau thời điểm hiện tại");
        }
        String note = request == null ? null : trim(request.note(), 500);

        DroneStationSuspension suspension = suspensions.findById(lockerId).orElseGet(DroneStationSuspension::new);
        suspension.setLockerId(lockerId);
        suspension.setReason(reason);
        suspension.setNote(note);
        suspension.setSuspendedByUserId(actorUserId);
        suspension.setSuspendedAt(now);
        suspension.setResumeAt(resumeAt);
        suspensions.save(suspension);
        log(lockerId, DroneStationSuspensionLog.SUSPEND, reason, note, resumeAt, actorUserId, now);
        return view(suspension);
    }

    /// Mở lại trạm; trạm không dừng thì không làm gì.
    @Transactional
    public void resume(Long lockerId, Long actorUserId) {
        DroneStationSuspension suspension = suspensions.findById(lockerId).orElse(null);
        if (suspension == null) {
            return;
        }
        suspensions.delete(suspension);
        log(lockerId, DroneStationSuspensionLog.RESUME, suspension.getReason(), null, null, actorUserId,
                LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<DroneStationSuspensionLog> suspensionLogs(Long lockerId, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 200)));
        return lockerId == null
                ? suspensionLogs.findAllByOrderByCreatedAtDescIdDesc(page)
                : suspensionLogs.findByLockerIdOrderByCreatedAtDescIdDesc(lockerId, page);
    }

    private void log(Long lockerId, String action, String reason, String note, LocalDateTime resumeAt, Long actor,
            LocalDateTime at) {
        DroneStationSuspensionLog entry = new DroneStationSuspensionLog();
        entry.setLockerId(lockerId);
        entry.setAction(action);
        entry.setReason(reason);
        entry.setNote(note);
        entry.setResumeAt(resumeAt);
        entry.setActorUserId(actor);
        entry.setCreatedAt(at);
        suspensionLogs.save(entry);
    }

    public static DroneFlightControlViews.StationSuspension view(DroneStationSuspension suspension) {
        return suspension == null ? null : new DroneFlightControlViews.StationSuspension(
                suspension.getLockerId(), suspension.getReason(), reasonLabel(suspension.getReason()),
                suspension.getNote(), suspension.getSuspendedByUserId(), suspension.getSuspendedAt(),
                suspension.getResumeAt());
    }

    public static String reasonLabel(String reason) {
        return SUSPENSION_REASONS.getOrDefault(reason, reason);
    }

    // ------------------------------------------------------------------ lịch sử chặn bay

    /// Thời tiết không cho bay (UNSAFE, hoặc UNAVAILABLE khi admin bật chặn lúc thiếu dữ liệu).
    public void recordWeather(String action, Context context, DroneWeatherAssessment weather) {
        String cause = weather.unavailable() ? DroneFlightBlock.WEATHER_UNAVAILABLE : DroneFlightBlock.WEATHER_UNSAFE;
        record(action, cause, context, null, weather.message(), weather);
    }

    public void recordSuspended(String action, Context context) {
        record(action, DroneFlightBlock.FLIGHTS_SUSPENDED, context, null,
                "Quản trị viên đang tạm dừng bay toàn hệ thống", null);
    }

    /// Ghi bằng giao dịch riêng; ghi lỗi chỉ để log, không đổi lỗi nghiệp vụ trả cho người dùng.
    public void record(String action, String cause, Context context, Long blockedLockerId, String message,
            DroneWeatherAssessment weather) {
        DroneFlightBlock block = new DroneFlightBlock();
        block.setCreatedAt(LocalDateTime.now());
        block.setAction(action);
        block.setCause(cause);
        if (context != null) {
            block.setOrderId(context.orderId());
            block.setOrderCode(context.orderCode());
            block.setActorUserId(context.actorUserId());
            block.setSourceLockerId(context.sourceLockerId());
            block.setDestinationLockerId(context.destinationLockerId());
        }
        block.setBlockedLockerId(blockedLockerId);
        block.setMessage(trim(message, 500));
        if (weather != null) {
            block.setWeatherStatus(weather.status());
            if (!weather.unavailable()) {
                block.setMaxWindSpeedKmh(weather.maxWindSpeedKmh());
                block.setMaxWindGustKmh(weather.maxWindGustKmh());
                block.setMaxPrecipitationMm(weather.maxPrecipitationMm());
                block.setWeatherCode(weather.worstWeatherCode());
            }
            block.setWeatherProvider(weather.provider());
        }
        try {
            TransactionTemplate separate = new TransactionTemplate(transactionManager);
            separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            separate.executeWithoutResult(status -> blocks.save(block));
        } catch (RuntimeException ex) {
            log.warn("Could not record drone flight block ({} / {}): {}", action, cause, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<DroneFlightBlock> searchBlocks(String cause, String action, Long lockerId, String orderCode,
            LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Pageable bounded = PageRequest.of(Math.max(0, pageable.getPageNumber()),
                Math.max(1, Math.min(pageable.getPageSize(), MAX_PAGE_SIZE)), pageable.getSort());
        return blocks.search(blankToNull(cause), blankToNull(action), lockerId, blankToNull(orderCode), from, to, bounded);
    }

    /// Số lần chặn theo nguyên nhân trong `hours` giờ qua.
    @Transactional(readOnly = true)
    public Map<String, Long> blockCounts(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(Math.max(1, Math.min(hours, 24 * 90)));
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (String cause : List.of(DroneFlightBlock.WEATHER_UNSAFE, DroneFlightBlock.WEATHER_UNAVAILABLE,
                DroneFlightBlock.FLIGHTS_SUSPENDED, DroneFlightBlock.STATION_SUSPENDED)) {
            counts.put(cause, 0L);
        }
        for (Object[] row : blocks.countByCauseSince(since)) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    /// Số lần chặn liên quan tới từng trạm (tủ gửi, tủ nhận hoặc trạm gây chặn) trong `hours` giờ qua; một lần
    /// chặn chỉ tính một lần cho mỗi trạm.
    @Transactional(readOnly = true)
    public Map<Long, Long> blockCountsByStation(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(Math.max(1, Math.min(hours, 24 * 90)));
        Map<Long, Long> counts = new java.util.TreeMap<>();
        for (Object[] row : blocks.stationsSince(since)) {
            java.util.Set<Long> stations = new java.util.HashSet<>();
            for (Object id : row) {
                if (id instanceof Number number) {
                    stations.add(number.longValue());
                }
            }
            stations.forEach(id -> counts.merge(id, 1L, Long::sum));
        }
        return counts;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String trim(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
