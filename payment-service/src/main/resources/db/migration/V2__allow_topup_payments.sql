-- Allow order_id = 0 for wallet topup payments (orderId = 0 is the sentinel value)
-- order_id already NOT NULL so no schema change needed; constraint satisfied by using 0.
-- Add index on reference_id for faster VNPay callback lookup
CREATE INDEX IF NOT EXISTS idx_payments_reference_id ON payment_schema.payments(reference_id);
