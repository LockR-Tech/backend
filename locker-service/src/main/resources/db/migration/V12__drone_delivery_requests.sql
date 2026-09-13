-- Yeu cau giao hang bang drone do KHACH tao tu mobile; doi bay (MAINTENANCE)
-- nhan hang doi nay de dieu phoi drone. Thay the mock in-memory tren mobile.
-- Trang thai: PENDING -> DISPATCHED -> DELIVERED; khach huy khi con PENDING.

CREATE TABLE locker_schema.drone_delivery_requests
(
    id                BIGSERIAL PRIMARY KEY,
    locker_id         BIGINT      NOT NULL,
    box_id            BIGINT,
    box_number        INT,
    requester_user_id BIGINT      NOT NULL,
    receiver_phone    VARCHAR(50),
    description       VARCHAR(500),
    status            VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    drone_unit_id     BIGINT,
    dispatched_by     BIGINT,
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP,
    FOREIGN KEY (locker_id) REFERENCES locker_schema.lockers (id),
    FOREIGN KEY (drone_unit_id) REFERENCES locker_schema.drone_units (id)
);

CREATE INDEX idx_drone_delivery_requests_status ON locker_schema.drone_delivery_requests (status);
CREATE INDEX idx_drone_delivery_requests_requester ON locker_schema.drone_delivery_requests (requester_user_id);
