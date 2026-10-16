-- Lưu vị trí GPS hiện tại và trạng thái ca làm việc (on_duty) của KTV Kiosk và KTV Drone.
-- Phục vụ điều phối nhiệm vụ, thu hồi kiện hàng và giám sát hiện trường.
CREATE TABLE IF NOT EXISTS locker_schema.technician_presences
(
    user_id BIGINT PRIMARY KEY,
    technician_type VARCHAR(50) NOT NULL,
    on_duty BOOLEAN NOT NULL DEFAULT FALSE,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    accuracy_m DOUBLE PRECISION,
    heading_deg DOUBLE PRECISION,
    speed_ms DOUBLE PRECISION,
    altitude_m DOUBLE PRECISION,
    battery_level INT,
    device_info VARCHAR(255),
    shift_started_at TIMESTAMP,
    shift_ended_at TIMESTAMP,
    last_located_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_technician_presences_duty_type
    ON locker_schema.technician_presences (on_duty, technician_type);

