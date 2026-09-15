package com.huynqb.laundrylocker.common.util;

import com.huynqb.laundrylocker.common.exception.BusinessException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;

/**
 * Quy đổi mốc thời gian cho các API báo cáo admin.
 *
 * <p>Cột thời gian trong DB là {@link LocalDateTime} "trần" ghi theo múi giờ của JVM
 * ({@code LocalDateTime.now()}); container production chạy UTC. Kinh doanh lại tính
 * ngày theo giờ Việt Nam, nên "hôm nay", "theo ngày", "tháng này" phải cắt theo
 * Asia/Ho_Chi_Minh rồi đổi ngược về múi giờ lưu trữ trước khi truy vấn.
 *
 * <p>Tham số {@code from}/{@code to} nhận ba dạng:
 * <ul>
 *   <li>{@code yyyy-MM-dd}: ngày theo giờ Việt Nam; {@code from} là đầu ngày,
 *       {@code to} bao trọn ngày đó (biên trên loại trừ = đầu ngày hôm sau).</li>
 *   <li>ISO có offset ({@code 2026-09-15T08:00:00+07:00}, {@code ...Z}): đúng thời điểm đó.</li>
 *   <li>ISO không offset ({@code 2026-09-15T08:00:00}): hiểu theo múi giờ lưu trữ (UTC ở
 *       production) — cùng quy ước với giá trị trả về trong JSON.</li>
 * </ul>
 * Mọi khoảng đều nửa mở: {@code from <= t < toExclusive}.
 */
public final class BusinessTime {

    public static final ZoneId DEFAULT_BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /// Số ngày tối đa (tính cả hai đầu) của một khoảng báo cáo.
    public static final int MAX_RANGE_DAYS = 366;

    private final ZoneId storageZone;
    private final ZoneId businessZone;
    private final Clock clock;

    public BusinessTime(ZoneId storageZone, ZoneId businessZone, Clock clock) {
        this.storageZone = storageZone;
        this.businessZone = businessZone;
        this.clock = clock;
    }

    public static BusinessTime system() {
        return new BusinessTime(ZoneId.systemDefault(), DEFAULT_BUSINESS_ZONE, Clock.systemUTC());
    }

    public ZoneId storageZone() {
        return storageZone;
    }

    public ZoneId businessZone() {
        return businessZone;
    }

    /** Thời điểm hiện tại theo múi giờ lưu trữ (so được với cột DB). */
    public LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(storageZone));
    }

    /** Ngày hôm nay theo giờ kinh doanh (Việt Nam). */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(businessZone));
    }

    /** Ngày kinh doanh chứa một mốc thời gian đọc từ DB. */
    public LocalDate businessDate(LocalDateTime stored) {
        if (stored == null) {
            return null;
        }
        return stored.atZone(storageZone).withZoneSameInstant(businessZone).toLocalDate();
    }

    /** Đầu ngày kinh doanh, quy về múi giờ lưu trữ. */
    public LocalDateTime startOfDay(LocalDate businessDate) {
        return businessDate.atStartOfDay(businessZone).withZoneSameInstant(storageZone).toLocalDateTime();
    }

    public LocalDate startOfWeek(LocalDate businessDate) {
        return businessDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public LocalDate startOfMonth(LocalDate businessDate) {
        return businessDate.withDayOfMonth(1);
    }

    /** Biên dưới (bao gồm) theo múi giờ lưu trữ; null nếu rỗng. */
    public LocalDateTime parseFrom(String value) {
        return parse(value, false);
    }

    /** Biên trên (loại trừ) theo múi giờ lưu trữ; null nếu rỗng. */
    public LocalDateTime parseToExclusive(String value) {
        return parse(value, true);
    }

    /**
     * Khoảng ngày kinh doanh (bao gồm hai đầu) cho báo cáo doanh thu. Chỉ nhận
     * {@code yyyy-MM-dd}; thiếu thì mặc định từ đầu tháng tới hôm nay.
     */
    public DateRange dateRange(String from, String to) {
        LocalDate today = today();
        LocalDate start = isBlank(from) ? startOfMonth(today) : parseDate(from, "from");
        LocalDate end = isBlank(to) ? today : parseDate(to, "to");
        if (end.isBefore(start)) {
            throw new BusinessException("INVALID_DATE_RANGE", "to must not be before from");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw new BusinessException("INVALID_DATE_RANGE", "date range must not exceed 366 days");
        }
        return new DateRange(start, end);
    }

    /** Khoảng [from, to] đổi sang nửa mở theo múi giờ lưu trữ. */
    public LocalDateTime rangeStart(DateRange range) {
        return startOfDay(range.from());
    }

    public LocalDateTime rangeEndExclusive(DateRange range) {
        return startOfDay(range.to().plusDays(1));
    }

    private LocalDateTime parse(String value, boolean upperBound) {
        if (isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.length() == 10) {
                LocalDate date = LocalDate.parse(trimmed);
                return startOfDay(upperBound ? date.plusDays(1) : date);
            }
            if (trimmed.endsWith("Z") || trimmed.matches(".*[+-]\\d{2}:\\d{2}$")) {
                return OffsetDateTime.parse(trimmed).atZoneSameInstant(storageZone).toLocalDateTime();
            }
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(
                    "INVALID_DATE", "Invalid date value '" + trimmed + "' (use yyyy-MM-dd or ISO-8601)");
        }
    }

    private LocalDate parseDate(String value, String name) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException("INVALID_DATE", name + " must be yyyy-MM-dd");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Khoảng ngày kinh doanh, bao gồm cả hai đầu. */
    public record DateRange(LocalDate from, LocalDate to) {

        public long days() {
            return java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        }

        /** Kỳ liền trước có cùng số ngày. */
        public DateRange previous() {
            long days = days();
            return new DateRange(from.minusDays(days), from.minusDays(1));
        }
    }
}
