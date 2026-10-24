-- Thử mở ô qua Bluetooth (F3-G11): admin cấp quyền cho từng tài khoản trên từng tủ; app xin mã mở
-- ô ngắn hạn (ký Ed25519) rồi ghi qua BLE cho Pi. Pi chỉ giữ khoá công khai nên lộ thẻ nhớ của Pi
-- cũng không tự làm ra mã mở được.
CREATE TABLE iot_schema.ble_test_grants
(
    id         BIGSERIAL PRIMARY KEY,
    locker_id  BIGINT    NOT NULL,
    user_id    BIGINT    NOT NULL,
    granted_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ble_test_grants_locker_user UNIQUE (locker_id, user_id)
);

CREATE INDEX idx_ble_test_grants_user ON iot_schema.ble_test_grants (user_id);

-- Một dòng duy nhất (id = 1): cặp khoá ký mã mở ô, sinh lần đầu cần dùng.
CREATE TABLE iot_schema.ble_signing_keys
(
    id          BIGINT PRIMARY KEY,
    algorithm   VARCHAR(20) NOT NULL,
    private_key TEXT        NOT NULL,
    public_key  VARCHAR(64) NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
