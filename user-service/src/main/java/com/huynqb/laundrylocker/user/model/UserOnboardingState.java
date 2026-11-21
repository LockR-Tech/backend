package com.huynqb.laundrylocker.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/// Trạng thái hướng dẫn lần đầu trên màn hình của một người dùng (V9, F3-G13). Một dòng / người dùng,
/// tạo khi người đó ghi lần đầu; chưa có dòng = mặc định (tự mở, chưa xem nhóm nào).
@Entity
@Table(name = "user_onboarding_state")
@Getter
@Setter
public class UserOnboardingState {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /// FALSE = "Bỏ qua vĩnh viễn": không tự mở trên mọi thiết bị.
    @Column(name = "auto_show", nullable = false)
    private boolean autoShow = true;

    /// Mã nhóm → phiên bản lớn nhất đã xem.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seen_groups", nullable = false, columnDefinition = "jsonb")
    private Map<String, Integer> seenGroups = new LinkedHashMap<>();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
