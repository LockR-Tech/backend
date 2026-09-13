-- Tối ưu DB: index trạng thái ô để liệt kê ô lỗi/ngưng dùng cho đội bảo trì.
-- Đối chiếu LockerBoxRepository.findByStatusAndActiveTrueOrderByLockerIdAscBoxNumberAsc
-- (openFaults). Index composite hiện có là (locker_id, cell_type, status) nên
-- không phục vụ được filter chỉ theo status.
CREATE INDEX IF NOT EXISTS idx_locker_boxes_status ON locker_schema.locker_boxes(status);
