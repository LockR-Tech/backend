-- Ghi chú kết thúc nhiệm vụ khi báo rơi kiện từng là tiếng Anh "Parcel drop incident DPI-…" (test 2026-11-02).
UPDATE order_schema.drone_missions
SET end_note = REPLACE(end_note, 'Parcel drop incident', 'Sự cố rơi kiện')
WHERE end_note LIKE '%Parcel drop incident%';
