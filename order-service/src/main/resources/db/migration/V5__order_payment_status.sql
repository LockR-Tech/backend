-- Track whether an order has been paid (driven by payment-service PAYMENT_COMPLETED events).
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS payment_status VARCHAR (20) NOT NULL DEFAULT 'UNPAID';
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP;
