-- ADR: Drone IoT Logs & Hardware Telemetry Events
-- Luu tru toan bo nhat ky IoT phan cung cua Drone (Pi Controller, Pixhawk Autopilot, Moc treo hang, Camera Aruco/Thi giac)
-- Thiet ke tinh gon: khong can cot payload va location_coordinates, toan bo chi tiet duoc ghi ro rang trong cot message
CREATE TABLE IF NOT EXISTS locker_schema.drone_iot_logs
(
    id         BIGSERIAL PRIMARY KEY,
    drone_id   BIGINT        NOT NULL,
    drone_code VARCHAR(50)   NOT NULL,
    component  VARCHAR(50)   NOT NULL, -- PI_CONTROLLER, PIXHAWK, CARGO_HOOK, VISION_CAMERA, BATTERY_POWER, TELEMETRY
    event_type VARCHAR(50)   NOT NULL, -- POWER_CONNECTED, POWER_DISCONNECTED, PIXHAWK_CONNECTED, PIXHAWK_DISCONNECTED, DETECT_TARGET, HOOK_RELEASE, HOOK_ATTACH, HOOK_JAMMED, MANUAL_OVERRIDE, ARMED, DISARMED...
    result     VARCHAR(30)   NOT NULL, -- ONLINE, OFFLINE, SUCCESS, FAILED, CONNECTED, DISCONNECTED, RELEASED, LOCKED, DETECTED, TIMEOUT, JAMMED, WARNING
    actor_type VARCHAR(30)   NOT NULL, -- SYSTEM, ORDER, USER, TECHNICIAN, AUTOPILOT
    actor_id   VARCHAR(100),           -- Pi-Daemon, ORDER#105, KTV#3, USER#8, Autopilot-Pixhawk
    order_id   BIGINT,                 -- ID don hang neu phat sinh tu don hang
    message    TEXT          NOT NULL, -- Thong diep chi tiet su kien, ho tro xuong dong
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (drone_id) REFERENCES locker_schema.drone_units (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_drone_iot_logs_drone_id ON locker_schema.drone_iot_logs (drone_id);
CREATE INDEX IF NOT EXISTS idx_drone_iot_logs_drone_code ON locker_schema.drone_iot_logs (drone_code);
CREATE INDEX IF NOT EXISTS idx_drone_iot_logs_component ON locker_schema.drone_iot_logs (component);
CREATE INDEX IF NOT EXISTS idx_drone_iot_logs_event_type ON locker_schema.drone_iot_logs (event_type);
CREATE INDEX IF NOT EXISTS idx_drone_iot_logs_created_at ON locker_schema.drone_iot_logs (created_at);
CREATE INDEX IF NOT EXISTS idx_drone_iot_logs_order_id ON locker_schema.drone_iot_logs (order_id);

