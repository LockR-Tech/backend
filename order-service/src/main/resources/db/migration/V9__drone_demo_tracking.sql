ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS fulfillment_mode VARCHAR (20) NOT NULL DEFAULT 'STANDARD';

ALTER TABLE order_schema.drone_missions
    ADD COLUMN IF NOT EXISTS drone_code VARCHAR (80);

CREATE INDEX IF NOT EXISTS idx_orders_drone_fulfillment_mode
    ON order_schema.orders (fulfillment_mode)
    WHERE type = 'DRONE_DELIVERY';
