package com.huynqb.laundrylocker.common.settings;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Bảng `system_settings` + `system_setting_audits` do Flyway của từng service tạo.
/// Tên bảng gắn schema của service vì JdbcTemplate không đi qua `hibernate.default_schema`.
@Component
@ConditionalOnProperty(name = "app.settings.scope")
public class JdbcSettingsStore implements SettingsStore {

    private final JdbcTemplate jdbcTemplate;
    private final String settingsTable;
    private final String auditTable;

    public JdbcSettingsStore(
            JdbcTemplate jdbcTemplate,
            @Value("${spring.jpa.properties.hibernate.default_schema:}") String schema) {
        this.jdbcTemplate = jdbcTemplate;
        String prefix = schema == null || schema.isBlank() ? "" : schema.trim() + ".";
        if (!prefix.isEmpty() && !prefix.matches("[A-Za-z_][A-Za-z0-9_]*\\.")) {
            throw new IllegalStateException("Invalid hibernate.default_schema for settings: " + schema);
        }
        this.settingsTable = prefix + "system_settings";
        this.auditTable = prefix + "system_setting_audits";
    }

    @Override
    public Map<String, StoredSetting> loadAll() {
        Map<String, StoredSetting> result = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT setting_key, setting_value, updated_by_user_id, updated_at FROM " + settingsTable,
                rs -> {
                    Timestamp updatedAt = rs.getTimestamp("updated_at");
                    long actor = rs.getLong("updated_by_user_id");
                    result.put(rs.getString("setting_key"), new StoredSetting(
                            rs.getString("setting_key"),
                            rs.getString("setting_value"),
                            rs.wasNull() ? null : actor,
                            updatedAt == null ? null : updatedAt.toLocalDateTime()));
                });
        return result;
    }

    @Override
    public void upsert(String key, String value, Long actorUserId) {
        jdbcTemplate.update(
                "INSERT INTO " + settingsTable + " (setting_key, setting_value, updated_by_user_id, updated_at) "
                        + "VALUES (?, ?, ?, ?) ON CONFLICT (setting_key) DO UPDATE SET "
                        + "setting_value = EXCLUDED.setting_value, updated_by_user_id = EXCLUDED.updated_by_user_id, "
                        + "updated_at = EXCLUDED.updated_at",
                key, value, actorUserId, Timestamp.valueOf(LocalDateTime.now()));
    }

    @Override
    public void delete(String key) {
        jdbcTemplate.update("DELETE FROM " + settingsTable + " WHERE setting_key = ?", key);
    }

    @Override
    public void audit(String key, String oldValue, String newValue, Long actorUserId) {
        jdbcTemplate.update(
                "INSERT INTO " + auditTable + " (setting_key, old_value, new_value, actor_user_id, changed_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                key, oldValue, newValue, actorUserId, Timestamp.valueOf(LocalDateTime.now()));
    }

    @Override
    public List<SettingAuditView> audits(String key, int limit) {
        String sql = "SELECT id, setting_key, old_value, new_value, actor_user_id, changed_at FROM " + auditTable
                + (key == null ? "" : " WHERE setting_key = ?") + " ORDER BY changed_at DESC, id DESC LIMIT ?";
        Object[] args = key == null ? new Object[] {limit} : new Object[] {key, limit};
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            long actor = rs.getLong("actor_user_id");
            Long actorId = rs.wasNull() ? null : actor;
            Timestamp changedAt = rs.getTimestamp("changed_at");
            return new SettingAuditView(
                    rs.getLong("id"),
                    rs.getString("setting_key"),
                    rs.getString("old_value"),
                    rs.getString("new_value"),
                    actorId,
                    changedAt == null ? null : changedAt.toLocalDateTime());
        }, args);
    }
}
