-- Drone fleet maintenance: theo doi "con drone" vat ly (pin, trang thai bay,
-- ky thuat vien phu trach) - khac voi o tu cellType=DRONE (cho hang drone tha xuong).
-- Pin/trang thai bay hien chua co telemetry that, duoc ky thuat vien nhap tay.

CREATE TABLE locker_schema.drone_units
(
    id                     BIGSERIAL PRIMARY KEY,
    locker_id              BIGINT      NOT NULL,
    code                   VARCHAR(50) NOT NULL UNIQUE,
    status                 VARCHAR(30) NOT NULL DEFAULT 'IDLE',
    battery_percent        INT         NOT NULL DEFAULT 100,
    fault_reason           VARCHAR(255),
    assigned_technician_id BIGINT,
    last_charged_at        TIMESTAMP,
    created_at             TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP,
    FOREIGN KEY (locker_id) REFERENCES locker_schema.lockers (id)
);

CREATE INDEX idx_drone_units_locker ON locker_schema.drone_units (locker_id);
CREATE INDEX idx_drone_units_status ON locker_schema.drone_units (status);

CREATE TABLE locker_schema.drone_maintenance_logs
(
    id            BIGSERIAL PRIMARY KEY,
    drone_unit_id BIGINT        NOT NULL,
    actor_user_id BIGINT,
    note          VARCHAR(2000) NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (drone_unit_id) REFERENCES locker_schema.drone_units (id)
);

CREATE INDEX idx_drone_maintenance_logs_drone ON locker_schema.drone_maintenance_logs (drone_unit_id);

-- Seed demo gan vao CAB-DEMO-01 de tab mobile co du lieu ngay (idempotent theo code).
WITH demo AS (SELECT id FROM locker_schema.lockers WHERE code = 'CAB-DEMO-01')
INSERT
INTO locker_schema.drone_units (locker_id, code, status, battery_percent, fault_reason)
SELECT demo.id, v.code, v.status, v.battery_percent, v.fault_reason
FROM demo,
     (VALUES ('DRONE-01', 'IDLE', 92, NULL),
             ('DRONE-02', 'CHARGING', 41, NULL),
             ('DRONE-03', 'FAULT', 15,
              'Cam bien GPS loi, can kiem tra lai')) AS v(code, status, battery_percent, fault_reason)
WHERE NOT EXISTS (SELECT 1 FROM locker_schema.drone_units WHERE code = v.code);
