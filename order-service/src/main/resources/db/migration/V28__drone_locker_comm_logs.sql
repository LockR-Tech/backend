-- Bảng nhật ký giao tiếp Drone và Tủ (Drone – Locker Communication Logs)
CREATE TABLE IF NOT EXISTS order_schema.drone_locker_comm_logs
(
    id                     BIGSERIAL PRIMARY KEY,
    timestamp              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    message_id             VARCHAR(64),
    correlation_id         VARCHAR(64),
    session_id             VARCHAR(64),
    order_id               BIGINT,
    journey_id             BIGINT,
    drone_id               VARCHAR(50),
    locker_id              BIGINT,
    compartment_id         BIGINT,
    marker_id              INTEGER,
    marker_dictionary      VARCHAR(50),
    source_device          VARCHAR(50),
    destination_device     VARCHAR(50),
    mqtt_topic             VARCHAR(255),
    event_type             VARCHAR(50) NOT NULL,
    direction              VARCHAR(10) NOT NULL, -- INBOUND, OUTBOUND
    payload                TEXT,
    publish_status         VARCHAR(30),
    ack_status             VARCHAR(30),
    processing_status      VARCHAR(30),
    error_code             VARCHAR(50),
    error_message          VARCHAR(500),
    retry_count            INTEGER DEFAULT 0,
    processing_duration_ms BIGINT,
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_comm_logs_session_id ON order_schema.drone_locker_comm_logs (session_id);
CREATE INDEX IF NOT EXISTS idx_comm_logs_order_id ON order_schema.drone_locker_comm_logs (order_id);
CREATE INDEX IF NOT EXISTS idx_comm_logs_event_type ON order_schema.drone_locker_comm_logs (event_type);
CREATE INDEX IF NOT EXISTS idx_comm_logs_timestamp ON order_schema.drone_locker_comm_logs (timestamp);

