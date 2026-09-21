package com.huynqb.laundrylocker.iot.settings;

import com.huynqb.laundrylocker.common.settings.SettingDefinition;
import com.huynqb.laundrylocker.common.settings.SettingsCatalog;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.huynqb.laundrylocker.common.settings.SettingDefinition.integer;

/// Quy tắc nghiệp vụ của iot-service admin chỉnh được (ADR-0005).
/// Key giữ nguyên tên property cũ ⇒ biến môi trường đang đặt trên VM vẫn là mặc định.
@Component
public class IotSettingsCatalog implements SettingsCatalog {

    public static final String LOCKOUT_MAX_ATTEMPTS = "app.iot.lockout.max-attempts";
    public static final String LOCKOUT_MINUTES = "app.iot.lockout.minutes";
    public static final String UNLOCK_WAIT_SECONDS = "app.iot.unlock-wait-seconds";
    public static final String DOOR_OPEN_TIMEOUT_SECONDS = "app.iot.door-open-timeout-seconds";
    public static final String KIOSK_CONFIRM_WINDOW_MINUTES = "app.iot.kiosk-confirm-window-minutes";

    private static final String UNLOCK = "Mở tủ & chống dò mã";

    @Override
    public List<SettingDefinition> definitions() {
        return List.of(
                integer(LOCKOUT_MAX_ATTEMPTS, UNLOCK, "Số lần nhập sai mã trước khi khoá ô",
                        "Nhập sai PIN/QR liên tiếp đến số lần này thì ô bị tạm khoá chống dò mã.", 5, 1, 50, "lần"),
                integer(LOCKOUT_MINUTES, UNLOCK, "Thời gian tạm khoá ô",
                        "Ô bị khoá trong khoảng này sau khi nhập sai quá số lần cho phép.", 15, 1, 1440, "phút"),
                integer(UNLOCK_WAIT_SECONDS, UNLOCK, "Thời gian chờ tủ phản hồi lệnh mở",
                        "Hết thời gian này mà tủ chưa báo kết quả thì lệnh mở bị coi là quá hạn (TIMEOUT). "
                                + "Đặt trên 20 giây cần tăng timeout gọi từ locker-service (APP_RESILIENCE4J_TL_TIMEOUT).",
                        20, 5, 60, "giây"),
                integer(DOOR_OPEN_TIMEOUT_SECONDS, UNLOCK, "Thời gian cửa ô mở chờ lấy/bỏ hàng",
                        "Giá trị `timeout` gửi xuống tủ trong lệnh mở cửa.", 15, 5, 120, "giây"),
                integer(KIOSK_CONFIRM_WINDOW_MINUTES, UNLOCK, "Thời hạn xác nhận tại tủ sau khi mở ô",
                        "Xác nhận đã bỏ hàng / kết thúc thuê trên màn hình tủ chỉ được chấp nhận trong "
                                + "khoảng này kể từ lần mở ô thành công gần nhất của đơn.",
                        15, 1, 120, "phút"));
    }
}
