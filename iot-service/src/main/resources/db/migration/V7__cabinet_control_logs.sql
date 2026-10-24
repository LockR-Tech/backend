-- Nhật ký điều khiển tủ trên trang admin "Điều khiển tủ": lệnh gửi từ web (source WEB) và việc tủ
-- thực sự làm — kích relay mở ô, chạy nắp (source CABINET). Tủ ghi ra bộ nhớ của Pi trước rồi mới
-- gửi lên khi có mạng, nên event_id duy nhất để gửi lại không bị trùng.
CREATE TABLE iot_schema.cabinet_control_logs
(
    id            BIGSERIAL PRIMARY KEY,
    locker_id     BIGINT           NOT NULL,
    event_id      VARCHAR(64)      NOT NULL UNIQUE,
    source        VARCHAR(20)      NOT NULL,
    actor_user_id BIGINT,
    action        VARCHAR(30)      NOT NULL,
    axis          INT,
    box_id        BIGINT,
    box_number    INT,
    revolutions   DOUBLE PRECISION,
    status        VARCHAR(20)      NOT NULL,
    message       VARCHAR(500),
    occurred_at   TIMESTAMP        NOT NULL,
    created_at    TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cabinet_control_logs_locker_time
    ON iot_schema.cabinet_control_logs (locker_id, occurred_at DESC);
