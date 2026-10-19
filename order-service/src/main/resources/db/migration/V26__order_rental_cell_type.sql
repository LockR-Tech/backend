-- Loại ô của đơn thuê, đọc từ ô thật lúc tạo đơn (không theo loại app gửi lên). Giá thuê,
-- gia hạn và phí ở quá giờ tính theo cột này; đơn cũ để trống thì order-service tra lại từ locker-service.
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS rental_cell_type VARCHAR(20);
