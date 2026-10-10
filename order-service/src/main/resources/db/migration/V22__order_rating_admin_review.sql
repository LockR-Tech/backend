-- Admin xử lý đánh giá dịch vụ (trang /admin/feedback, tab "Đánh giá dịch vụ"): trả lời khách,
-- đánh dấu đã xử lý. Đánh giá cũ mặc định "chưa xử lý".
ALTER TABLE order_schema.order_ratings
    ADD COLUMN admin_reply VARCHAR(2000),
    ADD COLUMN replied_at  TIMESTAMP,
    ADD COLUMN replied_by  BIGINT,
    ADD COLUMN resolved    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN resolved_at TIMESTAMP,
    ADD COLUMN resolved_by BIGINT,
    ADD COLUMN updated_at  TIMESTAMP;

CREATE INDEX idx_order_ratings_created_at ON order_schema.order_ratings (created_at DESC);
