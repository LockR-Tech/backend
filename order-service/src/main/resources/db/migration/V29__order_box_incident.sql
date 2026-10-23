-- Ô của đơn đang có hàng bị báo sự cố (khách báo qua đơn): mốc bắt đầu sự cố. Khác null ⇒ phí quá giờ
-- dừng ở mốc này, khách chưa phải trả/mở ô; KTV xử lý xong thì hạn lấy đồ được cộng bù thời gian kẹt
-- rồi cột về null.
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS box_incident_opened_at TIMESTAMP;

-- Đơn đang có hàng mà khách đã báo hỏng ô trước bản này: lấy lần báo đầu tiên trong lịch sử đơn.
-- Nếu phiếu đã được xử lý từ trước, admin gỡ bằng POST /api/admin/orders/{id}/box-incident-resolved.
UPDATE order_schema.orders o
SET box_incident_opened_at = h.first_report
FROM (SELECT order_id, MIN(created_at) AS first_report
      FROM order_schema.order_status_history
      WHERE note = 'Customer reported a fault on the assigned box'
      GROUP BY order_id) h
WHERE h.order_id = o.id
  AND o.status IN ('STORING', 'RETURNED')
  AND o.box_incident_opened_at IS NULL;
