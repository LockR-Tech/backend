ALTER TABLE order_schema.drone_missions
    ADD COLUMN IF NOT EXISTS last_loading_idempotency_key VARCHAR(120),
    ADD COLUMN IF NOT EXISTS payload_weight_grams INTEGER,
    ADD COLUMN IF NOT EXISTS seal_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS parcel_matched BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS payload_secured BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS compartment_locked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS loading_note VARCHAR(500),
    ADD COLUMN IF NOT EXISTS loaded_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS loaded_at TIMESTAMP;

ALTER TABLE order_schema.drone_missions
    DROP CONSTRAINT IF EXISTS chk_drone_mission_payload_weight;

ALTER TABLE order_schema.drone_missions
    ADD CONSTRAINT chk_drone_mission_payload_weight
        CHECK (payload_weight_grams IS NULL OR payload_weight_grams > 0);

-- Mission được nhận trước khi có bước loading chưa có bằng chứng an toàn.
-- Đưa về đúng bước chờ nạp để đội bay xác nhận, thay vì cho phóng hoặc làm kẹt mission.
UPDATE order_schema.drone_missions
SET status = 'AWAITING_LOADING',
    ready_to_launch_at = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'READY_TO_LAUNCH'
  AND loaded_at IS NULL;
