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

    // Quy tắc hoàn tiền & khiếu nại (ADR-0005)
    public static final String REFUND_ENABLED = "app.payment.refund-enabled";
    public static final String REFUND_MIN_AMOUNT = "app.payment.refund-min-amount";
    public static final String REFUND_MAX_DAYS = "app.payment.refund-max-days";
    public static final String REFUND_ALLOWED_REASONS = "app.payment.refund-allowed-reasons";
    public static final String REFUND_REQUIRE_TRANSFER_REF = "app.payment.refund-require-transfer-ref";

    private static final String TOPUP = "Nạp ví";
    private static final String METHODS = "Phương thức thanh toán";
    private static final String REFUND = "Hoàn tiền & khiếu nại";

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
                        "Danh sách cách nhau bởi dấu phẩy, nhận CASH, WALLET, VNPAY, MOMO, SEPAY. "
                                + "Thanh toán đơn bằng phương thức không có trong danh sách bị từ chối; giá trị lạ bị bỏ qua.",
                        "CASH,WALLET,VNPAY,MOMO,SEPAY", List.of()).asPublic(),
                bool(CASH_AUTO_COMPLETE, METHODS, "Tự hoàn tất thanh toán tiền mặt",
                        "Bật: thanh toán CASH được ghi nhận COMPLETED ngay. Tắt: giữ PENDING chờ nhân viên xác nhận.",
                        true),

                bool(REFUND_ENABLED, REFUND, "Bật tính năng hoàn tiền ngân hàng",
                        "Bật: cho phép khách gửi yêu cầu hoàn tiền khi có sự cố. Tắt: tạm khóa tạo yêu cầu hoàn tiền.",
                        true).asPublic(),
                integer(REFUND_MIN_AMOUNT, REFUND, "Số tiền hoàn tối thiểu",
                        "Yêu cầu hoàn tiền nhỏ hơn số này bị từ chối.", 1000, 1000, 100_000_000, "VND").asPublic(),
                integer(REFUND_MAX_DAYS, REFUND, "Thời hạn tối đa yêu cầu hoàn tiền",
                        "Số ngày tối đa kể từ khi phát sinh đơn hàng mà khách được gửi yêu cầu hoàn tiền.", 7, 1, 90, "ngày").asPublic(),
                string(REFUND_ALLOWED_REASONS, REFUND, "Danh sách lý do hoàn tiền gợi ý",
                        "Các lý do hoàn tiền hợp lệ hiển thị trên ứng dụng, phân tách bằng dấu phẩy.",
                        "Tủ lỗi không mở được,Không nhận được đồ giặt,Máy giặt gặp sự cố,Thanh toán trùng đơn,Phí lưu kho tính sai",
                        List.of()).asPublic(),
                bool(REFUND_REQUIRE_TRANSFER_REF, REFUND, "Bắt buộc nhập mã GD ngân hàng khi duyệt",
                        "Bật: Quản trị viên bắt buộc phải điền mã giao dịch / UNC ngân hàng trước khi xác nhận đã chuyển tiền.",
                        false));
    }
}
