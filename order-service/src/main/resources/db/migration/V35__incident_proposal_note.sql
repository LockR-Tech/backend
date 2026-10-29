-- Nội dung phương án admin gửi khách (test 2026-10-29): "Thỏa thuận riêng" trước đây không có chỗ ghi
-- thỏa thuận là gì, khách phải bấm Đồng ý một phương án trống. Bắt buộc với MANUAL_RESOLUTION, tuỳ chọn
-- với các loại khác (lời giải thích kèm phương án).
ALTER TABLE order_schema.drone_incident_resolution_proposals
    ADD COLUMN IF NOT EXISTS resolution_note VARCHAR(1000);
