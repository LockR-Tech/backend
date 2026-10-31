-- Toạ độ của kiện tại lúc ghi mỗi mốc trạng thái đơn drone (điểm lấy hàng, vị trí KTV, trạm/tủ gửi,
-- drone đang bay, tủ nhận). location_source cho biết toạ độ lấy từ đâu. Dòng cũ và đơn không phải drone
-- để NULL: không đoán lại vị trí trong quá khứ.
ALTER TABLE order_schema.order_status_history
    ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS location_source VARCHAR(30);
