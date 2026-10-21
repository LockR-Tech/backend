-- ADR: Drone Hardware & Companion Device (Raspberry Pi & Pixhawk)
-- Tuong tu nhu iot_schema.gateway_devices cua Kiosk, luu tru thiet bi bo dieu khien gan tren Drone.
CREATE TABLE IF NOT EXISTS locker_schema.drone_devices
(
    id                 BIGSERIAL PRIMARY KEY,
    mac_address        VARCHAR(17) NOT NULL UNIQUE,
    drone_id           BIGINT UNIQUE,
    drone_code         VARCHAR(50),
    ip_address         VARCHAR(45),
    firmware_version   VARCHAR(50) DEFAULT 'v1.0.0',
    mavlink_status     VARCHAR(30) DEFAULT 'DISCONNECTED',
    flight_mode        VARCHAR(30) DEFAULT 'UNKNOWN',
    armed              BOOLEAN     DEFAULT FALSE,
    landed_state       VARCHAR(30) DEFAULT 'ON_GROUND',
    gps_fix_type       INTEGER     DEFAULT 0,
    gps_satellites     INTEGER     DEFAULT 0,
    battery_percent    INTEGER     DEFAULT 100,
    voltage_v          NUMERIC(5, 2),
    current_a          NUMERIC(5, 2),
    camera_status      VARCHAR(30) DEFAULT 'OFFLINE',
    camera_stream_url  VARCHAR(255),
    last_seen_at       TIMESTAMP,
    created_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (drone_id) REFERENCES locker_schema.drone_units (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_drone_devices_drone_id ON locker_schema.drone_devices (drone_id);
CREATE INDEX IF NOT EXISTS idx_drone_devices_drone_code ON locker_schema.drone_devices (drone_code);

