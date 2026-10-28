package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.dto.DroneFlightControlViews;
import com.huynqb.laundrylocker.order.dto.DroneWeatherAssessment;
import com.huynqb.laundrylocker.order.dto.DroneWeatherViews;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Tự dừng / mở lại từng trạm theo dự báo tại trạm (`app.drone.weather.auto-station-pause`).
 *
 * <p>Trạm có dự báo vượt ngưỡng (UNSAFE) mà chưa dừng ⇒ hệ thống dừng với lý do WEATHER, người thao tác để trống
 * (null = hệ thống). Lệnh dừng của hệ thống được mở lại khi dự báo tại trạm đủ điều kiện bay; lệnh dừng admin tạo
 * thì để admin tự mở. Không có dữ liệu thời tiết (UNAVAILABLE) không đổi gì: bước kiểm tra lúc đặt đơn / phóng vẫn
 * chặn theo cấu hình, còn dừng trạm vì nguồn dự báo trục trặc thì dễ bật tắt liên tục.
 *
 * <p>Chặn bay theo tuyến lúc đặt đơn / phóng drone vẫn là chốt chặn chính; job này để admin, KTV và khách thấy
 * trạm nào đang không bay được, và để lịch sử dừng trạm ghi lại các đợt thời tiết xấu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DroneStationWeatherGuard {

    static final String AUTO_NOTE_PREFIX = "Tự động theo dự báo: ";

    private final OrderRules rules;
    private final DroneWeatherQueryService weatherQueryService;
    private final DroneFlightControl flightControl;
    private final DroneDispatchSweeper dispatchSweeper;

    @Scheduled(cron = "${app.drone.weather.station-guard-cron:0 */5 * * * *}")
    public void guardScheduled() {
        if (!rules.weatherAutoStationPause()) {
            return;
        }
        try {
            if (guard() > 0) {
                dispatchSweeper.reviewBlocksAt(LocalDateTime.now());
            }
        } catch (RuntimeException ex) {
            log.warn("Drone station weather guard failed: {}", ex.getMessage());
        }
    }

    /// @return số trạm vừa bị dừng hoặc được mở lại
    int guard() {
        int changed = 0;
        for (DroneWeatherViews.LockerWeather station : weatherQueryService.droneLockers()) {
            try {
                if (apply(station)) {
                    changed++;
                }
            } catch (RuntimeException ex) {
                log.warn("Weather guard could not update station {}: {}", station.lockerId(), ex.getMessage());
            }
        }
        return changed;
    }

    private boolean apply(DroneWeatherViews.LockerWeather station) {
        DroneWeatherAssessment weather = station.weather();
        if (weather == null || weather.unavailable()) {
            return false;
        }
        DroneFlightControlViews.StationSuspension suspension = station.suspension();
        if (suspension == null) {
            if (weather.safe()) {
                return false;
            }
            flightControl.suspend(station.lockerId(), new DroneFlightControlViews.SuspendStationRequest(
                    "WEATHER", AUTO_NOTE_PREFIX + weather.message(), null), null);
            log.info("Weather guard suspended drone station {}: {}", station.lockerId(), weather.message());
            return true;
        }
        if (weather.safe() && systemWeatherStop(suspension)) {
            flightControl.resume(station.lockerId(), null);
            log.info("Weather guard resumed drone station {}", station.lockerId());
            return true;
        }
        return false;
    }

    static boolean systemWeatherStop(DroneFlightControlViews.StationSuspension suspension) {
        return suspension.suspendedByUserId() == null && "WEATHER".equals(suspension.reason());
    }
}
