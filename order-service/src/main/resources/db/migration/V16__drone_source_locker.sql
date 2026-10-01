ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS source_locker_id BIGINT;

UPDATE orders o
SET source_locker_id = m.source_locker_id
FROM drone_missions m
WHERE o.id = m.order_id
  AND o.type = 'DRONE_DELIVERY'
  AND o.source_locker_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_orders_drone_source_locker
    ON orders(source_locker_id)
    WHERE type = 'DRONE_DELIVERY';
