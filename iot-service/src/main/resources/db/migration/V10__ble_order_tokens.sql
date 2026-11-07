-- Mở ô theo ĐƠN qua Bluetooth (F3-G11): mỗi mã cấp cho một đơn lưu nonce → đơn. Pi mở ô xong báo về
-- `cabinet/{lockerId}/event/control` (BOX_OPEN_BLE kèm nonce); backend khớp nonce ở đây rồi mới chuyển
-- trạng thái đơn như mở bằng PIN/QR. `processed_at` chống xử lý lặp khi Pi gửi lại sự kiện.
CREATE TABLE iot_schema.ble_order_tokens
(
    nonce        VARCHAR(16) PRIMARY KEY,
    order_id     BIGINT      NOT NULL,
    locker_id    BIGINT      NOT NULL,
    box_id       BIGINT      NOT NULL,
    box_number   INT         NOT NULL,
    user_id      BIGINT      NOT NULL,
    action       VARCHAR(20) NOT NULL,
    expires_at   TIMESTAMP   NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    result       VARCHAR(20)
);

CREATE INDEX idx_ble_order_tokens_order ON iot_schema.ble_order_tokens (order_id);
