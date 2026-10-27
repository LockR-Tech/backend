-- Cấu hình phần cứng của tủ sửa trên tab "Cấu hình phần cứng" (trang admin Điều khiển tủ):
-- config = bản đồ chân Pi thực sự dùng (gửi xuống bằng lệnh MQTT hw-config khi bấm Áp dụng),
-- wiring = sơ đồ đấu dây để tra cứu (chỉ lưu ở đây, Pi không dùng). Cả hai là JSON dạng text.
CREATE TABLE iot_schema.cabinet_hardware_configs
(
    locker_id     BIGINT       PRIMARY KEY,
    config        TEXT         NOT NULL,
    wiring        TEXT         NOT NULL,
    updated_by    BIGINT,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_by    BIGINT,
    applied_at    TIMESTAMP,
    apply_status  VARCHAR(20),
    apply_message VARCHAR(1000)
);
