package com.huynqb.laundrylocker.payment.settings;

import com.huynqb.laundrylocker.common.settings.SettingDefinition;
import com.huynqb.laundrylocker.common.settings.SettingsCatalog;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.huynqb.laundrylocker.common.settings.SettingDefinition.bool;
import static com.huynqb.laundrylocker.common.settings.SettingDefinition.integer;
import static com.huynqb.laundrylocker.common.settings.SettingDefinition.integerList;
import static com.huynqb.laundrylocker.common.settings.SettingDefinition.string;

/// Quy tắc nghiệp vụ của payment-service admin chỉnh được (ADR-0005).
/// Không gồm khoá/URL cổng thanh toán (VNPay, MoMo) — đó là cấu hình hạ tầng, giữ ở biến môi trường.
@Component
public class PaymentSettingsCatalog implements SettingsCatalog {

    public static final String TOPUP_MIN_AMOUNT = "app.payment.topup-min-amount";
    public static final String TOPUP_MAX_AMOUNT = "app.payment.topup-max-amount";
    public static final String TOPUP_DEFAULT_AMOUNT = "app.payment.topup-default-amount";
    public static final String TOPUP_PRESETS = "app.payment.topup-presets";
    public static final String ENABLED_METHODS = "app.payment.enabled-methods";
    public static final String CASH_AUTO_COMPLETE = "app.payment.cash-auto-complete";

    private static final String TOPUP = "Nạp ví";
    private static final String METHODS = "Phương thức thanh toán";

    @Override
    public List<SettingDefinition> definitions() {
        return List.of(
                integer(TOPUP_MIN_AMOUNT, TOPUP, "Số tiền nạp tối thiểu",
                        "Yêu cầu nạp ví nhỏ hơn số này bị từ chối.", 1000, 1000, 100_000_000, "VND").asPublic(),
                integer(TOPUP_MAX_AMOUNT, TOPUP, "Số tiền nạp tối đa mỗi lần",
                        "Yêu cầu nạp ví lớn hơn số này bị từ chối. Nhỏ hơn mức tối thiểu thì dùng mức tối thiểu.",
                        50_000_000, 1000, 1_000_000_000, "VND").asPublic(),
                integer(TOPUP_DEFAULT_AMOUNT, TOPUP, "Số tiền nạp mặc định trên app",
                        "Số tiền điền sẵn khi mở màn hình nạp ví.", 100_000, 1000, 1_000_000_000, "VND").asPublic(),
                integerList(TOPUP_PRESETS, TOPUP, "Nút chọn nhanh số tiền nạp",
                        "Danh sách số tiền hiển thị trên app, phân tách bằng dấu phẩy.",
                        "20000,50000,100000,200000,500000,1000000", 1000, 1_000_000_000, "VND").asPublic(),

                string(ENABLED_METHODS, METHODS, "Phương thức thanh toán đang bật",
                        "Danh sách cách nhau bởi dấu phẩy, chỉ nhận CASH, WALLET, VNPAY, MOMO. "
                                + "Thanh toán đơn bằng phương thức không có trong danh sách bị từ chối; giá trị lạ bị bỏ qua.",
                        "CASH,WALLET,VNPAY,MOMO", List.of()).asPublic(),
                bool(CASH_AUTO_COMPLETE, METHODS, "Tự hoàn tất thanh toán tiền mặt",
                        "Bật: thanh toán CASH được ghi nhận COMPLETED ngay. Tắt: giữ PENDING chờ nhân viên xác nhận.",
                        true));
    }
}
