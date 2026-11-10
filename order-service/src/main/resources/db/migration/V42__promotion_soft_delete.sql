-- Xoá khuyến mãi ở trang admin là xoá mềm: còn trong DB kèm lý do, thời điểm và người xoá (ADR-0009).
ALTER TABLE order_schema.promotions
    ADD COLUMN deleted_at TIMESTAMP,
    ADD COLUMN deleted_by BIGINT,
    ADD COLUMN deleted_reason VARCHAR(500);

-- Mã của khuyến mãi đã xoá được dùng lại cho khuyến mãi mới.
ALTER TABLE order_schema.promotions DROP CONSTRAINT promotions_code_key;
CREATE UNIQUE INDEX uq_promotions_code_not_deleted
    ON order_schema.promotions (code)
    WHERE deleted_at IS NULL;
