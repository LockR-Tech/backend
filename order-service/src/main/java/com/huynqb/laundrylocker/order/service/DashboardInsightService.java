package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.util.BusinessTime;
import com.huynqb.laundrylocker.common.util.BusinessTime.DateRange;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/// Số liệu phụ cho dashboard admin không nằm trong báo cáo doanh thu.
@Service
@RequiredArgsConstructor
public class DashboardInsightService {

    public record HourBucket(int hour, long orders) {
    }

    private final LockerOrderRepository orders;

    private BusinessTime time = BusinessTime.system();

    void setTime(BusinessTime time) {
        this.time = time;
    }

    /// Số đơn tạo theo từng giờ (0–23, giờ Việt Nam) trong khoảng from/to (yyyy-MM-dd, gồm hai
    /// đầu, mặc định đầu tháng tới hôm nay — cùng quy ước báo cáo doanh thu). Đủ 24 mốc.
    @Transactional(readOnly = true)
    public List<HourBucket> peakHours(String from, String to) {
        DateRange range = time.dateRange(from, to);
        LocalDateTime start = time.startOfDay(range.from());
        LocalDateTime end = time.startOfDay(range.to().plusDays(1));
        long[] counts = new long[24];
        for (LocalDateTime createdAt : orders.createdAtBetween(start, end)) {
            int hour = createdAt.atZone(time.storageZone()).withZoneSameInstant(time.businessZone()).getHour();
            counts[hour]++;
        }
        List<HourBucket> result = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            result.add(new HourBucket(hour, counts[hour]));
        }
        return result;
    }
}
