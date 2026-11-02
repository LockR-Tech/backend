-- Đường bay của từng đơn drone: mỗi điểm là vị trí drone lúc đó (telemetry thật hoặc điểm nội suy của bộ
-- giả lập DEMO), để xem lại sau chuyến bay. Trước bản này vị trí chỉ phát lên bản đồ rồi mất.
CREATE TABLE IF NOT EXISTS order_schema.drone_flight_track_points
(
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT           NOT NULL,
    mission_id      BIGINT,
    drone_code      VARCHAR(50),
    stage           VARCHAR(30)      NOT NULL,
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    altitude_m      DOUBLE PRECISION,
    heading_deg     DOUBLE PRECISION,
    speed_mps       DOUBLE PRECISION,
    battery_percent INTEGER,
    source          VARCHAR(30)      NOT NULL,
    recorded_at     TIMESTAMP        NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_drone_flight_track_order_time
    ON order_schema.drone_flight_track_points (order_id, recorded_at);
