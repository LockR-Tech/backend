-- reserved_order_id was considered too, but reserveBox(boxId) is called before
-- the order row exists (no id yet) -- see OrderService.create()/resolveAndReserveSendBox().
-- reserved_until alone is enough for a release-on-expiry backstop sweep.
ALTER TABLE locker_schema.locker_boxes
    ADD COLUMN reserved_until TIMESTAMP;

CREATE INDEX idx_locker_boxes_reserved_until ON locker_schema.locker_boxes (reserved_until) WHERE reserved_until IS NOT NULL;
