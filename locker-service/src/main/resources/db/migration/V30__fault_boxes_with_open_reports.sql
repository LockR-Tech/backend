-- Phiếu báo sự cố ô tạo qua màn "Báo sự cố" của tủ trước đây không đưa ô về FAULT: ô trống vẫn được
-- cấp cho đơn mới (ô drone có phiếu mở vẫn hiện "Sẵn sàng nhận Drone"), ô có hàng vẫn hiện như bình
-- thường. Đưa các ô đó về FAULT như đường báo hỏng chuẩn, nhớ trạng thái cũ để đóng phiếu là trả lại.
UPDATE locker_schema.locker_boxes b
SET pre_fault_status = CASE WHEN b.status = 'AVAILABLE' THEN NULL ELSE b.status END,
    status           = 'FAULT',
    fault_reason     = COALESCE(NULLIF(b.fault_reason, ''), LEFT(r.description, 500))
FROM (SELECT DISTINCT ON (box_id) box_id, description
      FROM locker_schema.locker_reports
      WHERE box_id IS NOT NULL
        AND status IN ('OPEN', 'IN_PROGRESS')
      ORDER BY box_id, created_at DESC) r
WHERE r.box_id = b.id
  AND b.status IN ('AVAILABLE', 'RESERVED', 'OCCUPIED');
