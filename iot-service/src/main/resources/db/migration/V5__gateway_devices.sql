-- ADR-0008: bộ điều khiển tủ (Raspberry Pi) tự báo mình qua MQTT `iot/{mac}/discovery/result`;
-- admin gán nó vào một tủ, iot-service gửi `iot/{mac}/command/setup` kèm sơ đồ ô.
-- Một Pi một tủ: locker_id UNIQUE (Postgres cho nhiều NULL = nhiều Pi chưa gán).
CREATE TABLE IF NOT EXISTS iot_schema.gateway_devices
(
    id                 BIGSERIAL PRIMARY KEY,
    mac_address        VARCHAR(17) NOT NULL UNIQUE,
    hardware           VARCHAR(30),
    firmware_version   VARCHAR(50),
    slave_id           INTEGER     NOT NULL DEFAULT 1,
    available_slots    INTEGER,
    reported_locker_id BIGINT,
    locker_id          BIGINT UNIQUE,
    setup_status       VARCHAR(20) NOT NULL DEFAULT 'NONE',
    setup_command_id   VARCHAR(64),
    setup_progress     VARCHAR(20),
    setup_result       TEXT,
    setup_requested_at TIMESTAMP,
    setup_finished_at  TIMESTAMP,
    last_seen_at       TIMESTAMP,
    created_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
