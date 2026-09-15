package com.huynqb.laundrylocker.common.util;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BusinessTimeTest {

    // 2026-09-15 20:30 UTC = 2026-09-16 03:30 giờ Việt Nam.
    private final BusinessTime time = new BusinessTime(
            ZoneOffset.UTC,
            BusinessTime.DEFAULT_BUSINESS_ZONE,
            Clock.fixed(Instant.parse("2026-09-15T20:30:00Z"), ZoneOffset.UTC));

    @Test
    void todayFollowsVietnamCalendarNotUtc() {
        assertEquals(LocalDate.of(2026, 9, 16), time.today());
        assertEquals(LocalDateTime.of(2026, 9, 15, 20, 30), time.now());
    }

    @Test
    void dateOnlyBoundsCoverTheWholeVietnamDay() {
        assertEquals(LocalDateTime.of(2026, 9, 14, 17, 0), time.parseFrom("2026-09-15"));
        assertEquals(LocalDateTime.of(2026, 9, 15, 17, 0), time.parseToExclusive("2026-09-15"));
    }

    @Test
    void offsetDateTimeIsConvertedAndNaiveDateTimeIsStorageZone() {
        assertEquals(LocalDateTime.of(2026, 9, 15, 1, 0), time.parseFrom("2026-09-15T08:00:00+07:00"));
        assertEquals(LocalDateTime.of(2026, 9, 15, 8, 0), time.parseFrom("2026-09-15T08:00:00Z"));
        assertEquals(LocalDateTime.of(2026, 9, 15, 8, 0), time.parseFrom("2026-09-15T08:00:00"));
        assertNull(time.parseFrom(" "));
    }

    @Test
    void businessDateOfLateUtcEveningIsNextVietnamDay() {
        assertEquals(LocalDate.of(2026, 9, 16), time.businessDate(LocalDateTime.of(2026, 9, 15, 17, 0)));
        assertEquals(LocalDate.of(2026, 9, 15), time.businessDate(LocalDateTime.of(2026, 9, 15, 16, 59)));
    }

    @Test
    void defaultRangeIsMonthToDateAndPreviousHasSameLength() {
        BusinessTime.DateRange range = time.dateRange(null, null);
        assertEquals(LocalDate.of(2026, 9, 1), range.from());
        assertEquals(LocalDate.of(2026, 9, 16), range.to());
        assertEquals(16, range.days());
        assertEquals(new BusinessTime.DateRange(LocalDate.of(2026, 8, 16), LocalDate.of(2026, 8, 31)), range.previous());
    }

    @Test
    void rejectsInvertedOrMalformedRange() {
        assertEquals("INVALID_DATE_RANGE",
                assertThrows(BusinessException.class, () -> time.dateRange("2026-09-10", "2026-09-01")).getCode());
        assertEquals("INVALID_DATE",
                assertThrows(BusinessException.class, () -> time.parseFrom("15/09/2026")).getCode());
    }
}
