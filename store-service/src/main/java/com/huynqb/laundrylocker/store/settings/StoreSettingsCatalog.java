package com.huynqb.laundrylocker.store.settings;

import com.huynqb.laundrylocker.common.settings.SettingDefinition;
import com.huynqb.laundrylocker.common.settings.SettingsCatalog;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.huynqb.laundrylocker.common.settings.SettingDefinition.decimal;

/// Quy tắc nghiệp vụ của store-service admin chỉnh được (ADR-0005).
@Component
public class StoreSettingsCatalog implements SettingsCatalog {

    public static final String NEARBY_DEFAULT_RADIUS_KM = "app.store.nearby-default-radius-km";

    private static final String NEARBY = "Tìm cửa hàng";

    @Override
    public List<SettingDefinition> definitions() {
        return List.of(
                decimal(NEARBY_DEFAULT_RADIUS_KM, NEARBY, "Bán kính tìm cửa hàng gần đây mặc định",
                        "Dùng khi app không gửi radiusKm; chỉ trả cửa hàng trong bán kính này.", "10", "0.5", "200", "km")
                        .asPublic());
    }
}
