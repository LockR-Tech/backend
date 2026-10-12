-- Người được ủy quyền lấy hộ (POST /api/orders/{id}/delegate). Trước đây việc ủy quyền chỉ ghi đè
-- receiver_phone/receiver_name nên không phân biệt được "người nhận" với "người lấy hộ"; các cột
-- này để người được ủy quyền thấy đơn trong app (GET /api/orders/delegated-to-me).
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS delegate_phone VARCHAR(255),
    ADD COLUMN IF NOT EXISTS delegate_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS delegate_note  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS delegated_at   TIMESTAMP;

-- Đơn đang chờ lấy đã được ủy quyền trước bản này: lấy lại từ nhật ký trạng thái. Lúc ủy quyền
-- receiver_phone đã bị ghi đè bằng số người lấy hộ nên dùng luôn giá trị đó.
UPDATE order_schema.orders o
SET delegate_phone = o.receiver_phone,
    delegate_name  = o.receiver_name,
    delegated_at   = h.delegated_at
FROM (SELECT order_id, MAX(created_at) AS delegated_at
      FROM order_schema.order_status_history
      WHERE note LIKE 'Delegated pickup to %'
      GROUP BY order_id) h
WHERE h.order_id = o.id
  AND o.status IN ('STORING', 'RETURNED')
  AND o.delegate_phone IS NULL
  AND o.receiver_phone IS NOT NULL;

-- Màn "Hàng đến" tra theo tài khoản người nhận. Số điện thoại so sánh sau khi bỏ dấu cách/gạch
-- nên không dùng được index thường trên cột đó.
CREATE INDEX IF NOT EXISTS idx_orders_receiver_user_id ON order_schema.orders (receiver_user_id);
CREATE INDEX IF NOT EXISTS idx_orders_receiver_id ON order_schema.orders (receiver_id);
