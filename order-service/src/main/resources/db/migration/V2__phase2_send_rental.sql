-- Phase 2: SEND/RENTAL flows + overdue reminder tracking
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS last_reminder_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_orders_status ON orders (status);
CREATE INDEX IF NOT EXISTS idx_orders_receiver_phone ON orders (receiver_phone);
CREATE INDEX IF NOT EXISTS idx_orders_type ON orders (type);
