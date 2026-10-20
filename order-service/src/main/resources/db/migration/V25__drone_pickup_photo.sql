ALTER TABLE order_schema.drone_pickups
    ADD COLUMN IF NOT EXISTS pickup_photo_public_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS pickup_photo_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS pickup_photo_captured_at TIMESTAMP;
