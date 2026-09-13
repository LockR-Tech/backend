-- PA3 cleanup: gỡ cột partner_id (di sản role PARTNER đã bỏ). Cột luôn NULL,
-- không còn nghiệp vụ nào dùng.
DROP INDEX IF EXISTS store_schema.idx_stores_partner_id;
ALTER TABLE store_schema.stores DROP COLUMN IF EXISTS partner_id;
