-- Mốc ô được mở lần đầu để bỏ hàng. Xác nhận bỏ hàng của đơn gửi (SEND) giờ đòi mốc
-- này, để app không xác nhận khi người gửi chưa hề mở ô (đơn sẽ đổi sang PIN nhận
-- trong lúc hàng chưa nằm trong ô).
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS drop_opened_at TIMESTAMP;

-- Đơn SEND đang dở lúc bản này lên được coi như đã mở ô, để người gửi đang đứng
-- trước tủ không bị kẹt bởi quy tắc mới.
UPDATE order_schema.orders
SET drop_opened_at = now()
WHERE type = 'SEND'
  AND status = 'INITIALIZED'
  AND drop_opened_at IS NULL;
