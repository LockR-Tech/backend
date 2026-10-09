-- Nhiệm vụ drone kết thúc không giao được hàng (đội bay huỷ trước khi phóng, hoặc chuyến bay
-- thất bại) nay được GIỮ LẠI thay vì xoá: cân nặng, niêm phong, người nạp là hồ sơ đối chứng.
ALTER TABLE order_schema.drone_missions
    ADD COLUMN IF NOT EXISTS ended_at TIMESTAMP;
ALTER TABLE order_schema.drone_missions
    ADD COLUMN IF NOT EXISTS ended_by_user_id BIGINT;
ALTER TABLE order_schema.drone_missions
    ADD COLUMN IF NOT EXISTS end_reason INTEGER;
ALTER TABLE order_schema.drone_missions
    ADD COLUMN IF NOT EXISTS end_note VARCHAR(500);
-- Chặng bay lúc chuyến bay thất bại; null với nhiệm vụ huỷ trước khi phóng.
ALTER TABLE order_schema.drone_missions
    ADD COLUMN IF NOT EXISTS failed_stage VARCHAR(40);
