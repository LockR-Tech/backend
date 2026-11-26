-- F3-G15: ô đã hết sự cố (status AVAILABLE) nhưng còn is_active = FALSE từ dữ liệu seed — app hiện "trống" mà đặt
-- thì bị từ chối ("Ô vừa được đặt"). restoreBox nay bật lại cờ; sửa các ô đang lệch.
UPDATE locker_schema.locker_boxes SET is_active = TRUE WHERE is_active = FALSE AND status = 'AVAILABLE';
