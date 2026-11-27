package com.huynqb.laundrylocker.locker.model;

import java.util.Map;

/// Thực thể được {@link ActionAuditListener} ghi nhật ký thao tác (F3-G16): giữ ảnh các trường cần theo dõi lúc
/// nạp từ DB để biết trường nào vừa đổi, từ giá trị nào sang giá trị nào.
public interface AuditTracked {

    /// Giá trị hiện tại của các trường cần theo dõi (khoá ổn định, giá trị dạng chuỗi, null = trống).
    Map<String, String> auditFields();

    Map<String, String> getAuditBaseline();

    void setAuditBaseline(Map<String, String> baseline);
}
