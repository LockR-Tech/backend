-- Bổ sung các trường lưu ảnh minh chứng của Admin, ảnh đính kèm của Customer và user_id cho yêu cầu hoàn tiền
ALTER TABLE payment_schema.refunds
    ADD COLUMN IF NOT EXISTS user_id BIGINT,
    ADD COLUMN IF NOT EXISTS proof_image_url TEXT,
    ADD COLUMN IF NOT EXISTS customer_attachment_url TEXT;

CREATE INDEX IF NOT EXISTS idx_refunds_user_id ON payment_schema.refunds(user_id);
