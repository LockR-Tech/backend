ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS receiver_user_id BIGINT;
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS destination_locker_id BIGINT;
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS reserved_box_id BIGINT;
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS parcel_weight_grams INTEGER;
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS delivery_stage VARCHAR (40);
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR (120);

CREATE INDEX IF NOT EXISTS idx_orders_delivery_stage ON order_schema.orders (delivery_stage);
CREATE INDEX IF NOT EXISTS idx_orders_destination_locker_id ON order_schema.orders (destination_locker_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_user_idempotency_key
    ON order_schema.orders (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
