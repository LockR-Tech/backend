-- Kiện rơi đã tìm thấy được điều phối về trạm bay gần nơi tìm thấy nhất còn drone (chọn lúc admin xác minh
-- kết quả thu hồi). recovery_locker_id trỏ tới trạm đó; các cột dưới là ảnh chụp trạm lúc điều phối để app
-- KTV hiện đường tới trạm mà không phải tra locker-service cho từng sự cố, và để biết trạm có drone hay
-- đang phải chờ drone.
ALTER TABLE order_schema.drone_parcel_incidents
    ADD COLUMN IF NOT EXISTS recovery_station_dispatched_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS recovery_station_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS recovery_station_address VARCHAR(500),
    ADD COLUMN IF NOT EXISTS recovery_station_latitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS recovery_station_longitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS recovery_station_distance_m INTEGER,
    ADD COLUMN IF NOT EXISTS recovery_station_drone_available BOOLEAN;
