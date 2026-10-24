-- Phiên giao hàng drone ↔ tủ (nhận diện ArUco → mở nắp → drone nhả hàng → đóng nắp).
-- Trước đây phiên chỉ nằm trong bộ nhớ của order-service: khởi động lại giữa chừng là mất phiên,
-- kết quả đóng nắp tới sau không còn gắn được với đơn. Lưu vào database để:
--   * kết quả mở/đóng nắp của tủ chỉ được chấp nhận khi khớp đúng mã lệnh server đã gửi;
--   * restart / nhiều instance không làm mất phiên;
--   * admin xem được phiên đang chạy.
CREATE TABLE IF NOT EXISTS order_schema.drone_delivery_sessions
(
    session_id        VARCHAR(64) PRIMARY KEY,
    correlation_id    VARCHAR(64),
    order_id          BIGINT       NOT NULL,
    mission_id        BIGINT       NOT NULL,
    drone_id          VARCHAR(50)  NOT NULL,
    drone_unit_id     BIGINT,
    locker_id         BIGINT       NOT NULL,
    compartment_id    BIGINT       NOT NULL,
    target_marker_id  INTEGER,
    target_dictionary VARCHAR(50),
    mode              VARCHAR(30)  NOT NULL,
    state             VARCHAR(30)  NOT NULL,
    open_command_id   VARCHAR(64),
    close_command_id  VARCHAR(64),
    lid_opened_at     TIMESTAMP,
    completed_at      TIMESTAMP,
    error_code        VARCHAR(50),
    error_message     VARCHAR(500),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_drone_delivery_sessions_order ON order_schema.drone_delivery_sessions (order_id);
CREATE INDEX IF NOT EXISTS idx_drone_delivery_sessions_drone ON order_schema.drone_delivery_sessions (drone_id, state);
CREATE UNIQUE INDEX IF NOT EXISTS uq_drone_delivery_sessions_open_cmd
    ON order_schema.drone_delivery_sessions (open_command_id) WHERE open_command_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_drone_delivery_sessions_close_cmd
    ON order_schema.drone_delivery_sessions (close_command_id) WHERE close_command_id IS NOT NULL;

-- Chống phát lại sự kiện drone: một messageId chỉ được xử lý một lần.
CREATE INDEX IF NOT EXISTS idx_comm_logs_message_direction
    ON order_schema.drone_locker_comm_logs (message_id, direction);
