ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS rental_duration_hours INTEGER;
