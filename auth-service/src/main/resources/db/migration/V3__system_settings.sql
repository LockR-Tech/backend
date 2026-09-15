-- Quy tắc nghiệp vụ admin cấu hình được (ADR-0005). Chỉ lưu giá trị admin đã ghi đè;
-- key không có dòng nào ⇒ dùng property/mặc định trong code. Bảng mới, tương thích ngược.
CREATE TABLE IF NOT EXISTS auth_schema.system_settings
(
    setting_key        VARCHAR(150)  PRIMARY KEY,
    setting_value      VARCHAR(4000) NOT NULL,
    updated_by_user_id BIGINT,
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth_schema.system_setting_audits
(
    id            BIGSERIAL PRIMARY KEY,
    setting_key   VARCHAR(150)  NOT NULL,
    old_value     VARCHAR(4000),
    new_value     VARCHAR(4000),
    actor_user_id BIGINT,
    changed_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_system_setting_audits_key ON auth_schema.system_setting_audits (setting_key, changed_at DESC);
