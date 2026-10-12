-- Đơn drone lấy hàng tận nhà: người gửi không còn tự mang kiện ra tủ. KTV drone đang rảnh, gần
-- người gửi nhất tới lấy kiện, mang về tủ gần người gửi nhất rồi phóng drone.

-- Thông tin liên hệ người gửi (bắt buộc với đơn mới) và điểm lấy hàng.
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS sender_name VARCHAR(120);
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS sender_phone VARCHAR(20);
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS sender_email VARCHAR(255);
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS pickup_address VARCHAR(500);
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS pickup_latitude DOUBLE PRECISION;
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS pickup_longitude DOUBLE PRECISION;
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS pickup_note VARCHAR(500);
-- Khoảng cách đường chim bay điểm lấy hàng → tủ gửi hệ thống chọn.
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS pickup_distance_meters INTEGER;

-- Đơn đã thanh toán nhưng đang không phục vụ được (phiếu sự cố trên drone/tủ/bãi đáp, tạm dừng
-- bay…). Hết sự cố thì xoá; quá hạn hoặc điều phối/admin xác nhận thì đơn "giao không hoàn tất".
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS delivery_block_reason VARCHAR(40);
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS delivery_blocked_at TIMESTAMP;
-- Phiếu sự cố (locker_schema.locker_reports.id) gây chặn, nếu xác định được.
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS delivery_block_ticket_id BIGINT;
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS delivery_block_note VARCHAR(500);
-- Lý do đơn kết thúc "giao không hoàn tất" và phiếu sự cố liên quan.
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(40);
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS failure_ticket_id BIGINT;
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS failed_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_orders_delivery_block_ticket
    ON order_schema.orders (delivery_block_ticket_id);
CREATE INDEX IF NOT EXISTS idx_orders_failure_ticket
    ON order_schema.orders (failure_ticket_id);

-- Một lần lấy hàng tận nhà cho mỗi đơn drone, tạo khi đơn được thanh toán.
CREATE TABLE IF NOT EXISTS order_schema.drone_pickups
(
    id                         BIGSERIAL PRIMARY KEY,
    order_id                   BIGINT       NOT NULL UNIQUE,
    -- SEARCHING | OFFERED | ACCEPTED | PICKED_UP | FAILED | CANCELED
    status                     VARCHAR(30)  NOT NULL,
    -- KTV đang được mời (OFFERED) hoặc đã nhận việc (ACCEPTED/PICKED_UP).
    technician_id              BIGINT,
    -- Khoảng cách KTV → điểm lấy hàng lúc được mời.
    technician_distance_meters INTEGER,
    -- Mã người gửi đọc cho KTV lúc giao kiện: đúng người, đúng đơn.
    pickup_code                VARCHAR(6)   NOT NULL,
    offered_at                 TIMESTAMP,
    offer_expires_at           TIMESTAMP,
    accepted_at                TIMESTAMP,
    picked_up_at               TIMESTAMP,
    -- Admin gán tay thay cho hệ thống tự mời.
    assigned_by_user_id        BIGINT,
    failed_at                  TIMESTAMP,
    failure_reason             VARCHAR(40),
    failure_note               VARCHAR(500),
    pickup_note                VARCHAR(500),
    created_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_drone_pickups_status
    ON order_schema.drone_pickups (status);
CREATE INDEX IF NOT EXISTS idx_drone_pickups_technician
    ON order_schema.drone_pickups (technician_id, status);

-- Từng lượt mời một KTV: ai đã từ chối/để quá hạn thì không mời lại cho cùng lần lấy hàng.
CREATE TABLE IF NOT EXISTS order_schema.drone_pickup_offers
(
    id              BIGSERIAL PRIMARY KEY,
    pickup_id       BIGINT      NOT NULL,
    technician_id   BIGINT      NOT NULL,
    -- OFFERED | ACCEPTED | DECLINED | EXPIRED | RELEASED | CANCELED
    status          VARCHAR(20) NOT NULL,
    distance_meters INTEGER,
    offered_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_drone_pickup_offers_pickup
    ON order_schema.drone_pickup_offers (pickup_id);

-- KTV drone đang trong ca và vị trí gần nhất app gửi lên — cơ sở để chọn người gần người gửi nhất.
CREATE TABLE IF NOT EXISTS order_schema.drone_technician_presence
(
    user_id         BIGINT PRIMARY KEY,
    on_duty         BOOLEAN   NOT NULL DEFAULT FALSE,
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    location_at     TIMESTAMP,
    duty_changed_at TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
