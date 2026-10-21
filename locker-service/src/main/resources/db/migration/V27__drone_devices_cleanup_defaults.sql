-- Bo cac gia tri mac dinh gay hieu nham (hardcode) trong bang drone_devices
-- Khi Pi tat nguon hoac Pixhawk chua ket noi, cac gia tri phai la NULL, khong duoc tu dien gia tri gia mao.
ALTER TABLE locker_schema.drone_devices ALTER COLUMN battery_percent DROP DEFAULT;
ALTER TABLE locker_schema.drone_devices ALTER COLUMN armed DROP DEFAULT;
ALTER TABLE locker_schema.drone_devices ALTER COLUMN landed_state DROP DEFAULT;
ALTER TABLE locker_schema.drone_devices ALTER COLUMN flight_mode DROP DEFAULT;
ALTER TABLE locker_schema.drone_devices ALTER COLUMN gps_fix_type DROP DEFAULT;
ALTER TABLE locker_schema.drone_devices ALTER COLUMN gps_satellites DROP DEFAULT;
ALTER TABLE locker_schema.drone_devices ALTER COLUMN firmware_version DROP DEFAULT;

-- Don dep cac gia tri gia mao ton tai tu truoc khi Pixhawk khong ket noi
UPDATE locker_schema.drone_devices
SET battery_percent = NULL,
    armed = NULL,
    landed_state = NULL,
    flight_mode = NULL,
    gps_fix_type = NULL,
    gps_satellites = NULL,
    voltage_v = NULL,
    current_a = NULL
WHERE mavlink_status = 'DISCONNECTED' OR mavlink_status IS NULL;

