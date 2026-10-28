-- Điều khiển bay theo trạm và lịch sử chặn bay (F1-G13).
--
-- Trạm bay = tủ có bãi đáp. Admin tạm dừng từng trạm (thời tiết cục bộ, bảo trì bãi đáp, lệnh cấm bay
-- khu vực…) thay vì tắt bay cả hệ thống. Có dòng ⇒ trạm đang dừng; `resume_at` (nếu có) là lúc tự mở lại.
CREATE TABLE IF NOT EXISTS order_schema.drone_station_suspensions
(
    locker_id            BIGINT PRIMARY KEY,
    reason               VARCHAR(30)  NOT NULL,
    note                 VARCHAR(500),
    suspended_by_user_id BIGINT,
    suspended_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resume_at            TIMESTAMP
);

-- Ai dừng / mở lại trạm nào, lúc nào.
CREATE TABLE IF NOT EXISTS order_schema.drone_station_suspension_logs
(
    id            BIGSERIAL PRIMARY KEY,
    locker_id     BIGINT      NOT NULL,
    action        VARCHAR(20) NOT NULL,
    reason        VARCHAR(30),
    note          VARCHAR(500),
    resume_at     TIMESTAMP,
    actor_user_id BIGINT,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_drone_station_suspension_logs_locker
    ON order_schema.drone_station_suspension_logs (locker_id, created_at DESC);

-- Mỗi lần hệ thống từ chối bay: đặt đơn, đội bay nhận đơn, phóng drone, hoặc đơn đang chạy bị chặn.
-- Đặt đơn bị chặn thì chưa có đơn (order_id NULL) — ghi người đặt và hai tủ của tuyến.
CREATE TABLE IF NOT EXISTS order_schema.drone_flight_blocks
(
    id                    BIGSERIAL PRIMARY KEY,
    created_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    action                VARCHAR(20) NOT NULL,
    cause                 VARCHAR(30) NOT NULL,
    order_id              BIGINT,
    order_code            VARCHAR(50),
    actor_user_id         BIGINT,
    source_locker_id      BIGINT,
    destination_locker_id BIGINT,
    blocked_locker_id     BIGINT,
    message               VARCHAR(500),
    weather_status        VARCHAR(20),
    max_wind_speed_kmh    DOUBLE PRECISION,
    max_wind_gust_kmh     DOUBLE PRECISION,
    max_precipitation_mm  DOUBLE PRECISION,
    weather_code          INTEGER,
    weather_provider      VARCHAR(20)
);

CREATE INDEX IF NOT EXISTS idx_drone_flight_blocks_created ON order_schema.drone_flight_blocks (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_drone_flight_blocks_order ON order_schema.drone_flight_blocks (order_id);
CREATE INDEX IF NOT EXISTS idx_drone_flight_blocks_cause ON order_schema.drone_flight_blocks (cause, created_at DESC);
