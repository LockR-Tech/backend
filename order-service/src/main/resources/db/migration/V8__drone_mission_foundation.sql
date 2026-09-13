CREATE TABLE IF NOT EXISTS order_schema.drone_missions
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    order_id
    BIGINT
    NOT
    NULL
    UNIQUE,
    drone_unit_id
    BIGINT,
    source_locker_id
    BIGINT,
    destination_locker_id
    BIGINT
    NOT
    NULL,
    status
    VARCHAR
(
    40
) NOT NULL,
    assigned_by_user_id BIGINT,
    last_accept_idempotency_key VARCHAR
(
    120
),
    last_launch_idempotency_key VARCHAR
(
    120
),
    ready_to_launch_at TIMESTAMP,
    launching_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_drone_missions_status
    ON order_schema.drone_missions (status);
