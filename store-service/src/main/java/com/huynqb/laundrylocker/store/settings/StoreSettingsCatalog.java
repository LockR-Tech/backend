package com.huynqb.laundrylocker.store.settings;

import com.huynqb.laundrylocker.common.settings.SettingDefinition;
import com.huynqb.laundrylocker.common.settings.SettingsCatalog;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.huynqb.laundrylocker.common.settings.SettingDefinition.bool;
import static com.huynqb.laundrylocker.common.settings.SettingDefinition.decimal;
import static com.huynqb.laundrylocker.common.settings.SettingDefinition.integerList;

/// Quy tắc nghiệp vụ của store-service admin chỉnh được (ADR-0005).
@Component
public class StoreSettingsCatalog implements SettingsCatalog {

    public static final String NEARBY_DEFAULT_RADIUS_KM = "app.store.nearby-default-radius-km";
    public static final String DEMO_VISIBLE = "app.store.demo.visible";
    public static final String DEMO_STORE_IDS = "app.store.demo.store-ids";
    public static final String DEMO_ALLOWED_USER_IDS = "app.store.demo.allowed-user-ids";

    private static final String NEARBY = "Tìm cửa hàng";
    private static final String DEMO = "Giả lập & demo";

    /// Điểm gửi demo của bộ seed `private/seed-demo` (LockR …, id 800001–800007).
    static final String SEED_DEMO_STORE_IDS = "800001,800002,800003,800004,800005,800006,800007";
    /// Tài khoản demo đăng nhập được (`*@demo.lockr.test`): 4 KTV tủ, 4 KTV drone, khách demo.
    static final String SEED_DEMO_USER_IDS = "800011,800012,800013,800014,800021,800022,800023,800024,800101";

    @Override
    public List<SettingDefinition> definitions() {
        return List.of(
                decimal(NEARBY_DEFAULT_RADIUS_KM, NEARBY, "Bán kính tìm cửa hàng gần đây mặc định",
                        "Dùng khi app không gửi radiusKm; chỉ trả cửa hàng trong bán kính này.", "10", "0.5", "200", "km")
                        .asPublic(),
                // Cụm "Giả lập & demo" trên web admin tắt/bật cờ này cùng các cờ giả lập khác (F3-G14).
                bool(DEMO_VISIBLE, DEMO, "Hiện điểm gửi demo cho khách",
                        "Tắt khi vận hành chính thức: điểm gửi trong danh sách demo biến khỏi app (mục Điểm gửi, bản đồ); "
                                + "admin và tài khoản trong danh sách được phép vẫn thấy để demo.", true),
                integerList(DEMO_STORE_IDS, DEMO, "Danh sách điểm gửi demo",
                        "Id điểm gửi demo, cách nhau bởi dấu phẩy. 0 = không có.", SEED_DEMO_STORE_IDS, 0, null, null),
                integerList(DEMO_ALLOWED_USER_IDS, DEMO, "Tài khoản vẫn thấy điểm gửi demo khi đã ẩn",
                        "Id người dùng vẫn thấy điểm gửi demo khi đã ẩn (mặc định: các tài khoản *@demo.lockr.test). "
                                + "0 = chỉ admin.", SEED_DEMO_USER_IDS, 0, null, null));
    }
}
