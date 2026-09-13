-- GAP 2: physical/hardware state reported by the cabinet (door/sensor),
-- kept SEPARATE from locker-service's order-driven LockerBox.status so the
-- hardware truth never overwrites the logical order state. One row per box,
-- upserted on each `cabinet/{lockerId}/locker/{boxId}/status` report.
CREATE TABLE iot_schema.box_hardware_status
(
    box_id           BIGINT PRIMARY KEY,
    locker_id        BIGINT,
    hw_state         VARCHAR(30) NOT NULL,
    last_reported_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_box_hardware_status_locker_id ON iot_schema.box_hardware_status (locker_id);
