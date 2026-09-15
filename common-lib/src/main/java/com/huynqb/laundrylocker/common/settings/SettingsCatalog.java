package com.huynqb.laundrylocker.common.settings;

import java.util.List;

/// Mỗi service khai báo đúng một catalog các quy tắc nghiệp vụ nó sở hữu (một `@Component`).
public interface SettingsCatalog {

    List<SettingDefinition> definitions();
}
