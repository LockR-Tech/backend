-- Tách "dừng phí quá giờ" khỏi "khoá mở ô bằng mã". Khách báo ô ⇒ ô chờ KTV kiểm tra: phí dừng từ lúc báo
-- nhưng chủ đồ vẫn mở ô được. Chỉ khi sự cố được xác nhận (KTV khoá ô, tủ báo hỏng phần cứng) mới khoá mở ô.
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS box_incident_blocks_access BOOLEAN NOT NULL DEFAULT FALSE;

-- Sự cố đang mở từ trước bản này đều đã khoá mở ô (ô bị đưa FAULT ngay khi báo): giữ nguyên hành vi.
UPDATE order_schema.orders
SET box_incident_blocks_access = TRUE
WHERE box_incident_opened_at IS NOT NULL;
