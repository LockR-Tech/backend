-- Trao trả đồ lưu kho tại quầy (đơn EXPIRED → COMPLETED): ai tới nhận, SĐT đã đối chiếu, ảnh xác minh,
-- số tiền admin khai đã thu. Mỗi đơn trao trả một lần.
CREATE TABLE IF NOT EXISTS order_schema.storage_handovers
(
    id                     BIGSERIAL PRIMARY KEY,
    order_id               BIGINT        NOT NULL UNIQUE,
    recipient_type         VARCHAR(20)   NOT NULL,
    recipient_name         VARCHAR(120)  NOT NULL,
    recipient_phone        VARCHAR(20),
    photo_public_id        VARCHAR(255)  NOT NULL UNIQUE,
    photo_url              VARCHAR(1000) NOT NULL,
    collected_amount       NUMERIC(12, 2),
    collection_method      VARCHAR(20),
    note                   VARCHAR(500),
    handed_over_by_user_id BIGINT        NOT NULL,
    handed_over_at         TIMESTAMP     NOT NULL
);
