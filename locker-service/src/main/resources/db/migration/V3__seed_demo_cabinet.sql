-- Seed tủ demo theo bản vẽ thiết kế (1.5m x 1.2m x 0.5m):
--  - Bãi đáp drone trên nóc (ArUco)
--  - Hàng 1 (row 1): 3 ô DRONE chỉ nhận hàng từ drone thả xuống
--  - Hàng 2-3: 6 ô STANDARD đa dịch vụ (giặt ủi, gửi/nhận hàng, thuê tủ...)
--  - Khu trái: 1 ô XL chứa vali (0.30 x 0.80 x 0.40)
-- Chỉ seed khi chưa có tủ demo (idempotent theo code).

INSERT INTO locker_schema.lockers (store_id, code, name, status, address, latitude, longitude, description, landing_pad,
                                   landing_marker_id)
SELECT 1,
       'CAB-DEMO-01',
       'Tu demo capstone 3x3 + vali',
       'ACTIVE',
       'FPT University HCMC',
       10.8411,
       106.8097,
       'Tu demo theo ban ve: 1.5x1.2x0.5m, bai dap drone tren noc, hang 1 danh cho drone',
       TRUE,
       'ARUCO-23' WHERE NOT EXISTS (SELECT 1 FROM locker_schema.lockers WHERE code = 'CAB-DEMO-01');

WITH demo AS (SELECT id FROM locker_schema.lockers WHERE code = 'CAB-DEMO-01')
INSERT
INTO locker_schema.locker_boxes (locker_id, box_number, size, status, cell_type, row_index, col_index, description)
SELECT demo.id,
       v.box_number,
       v.size,
       'AVAILABLE',
       v.cell_type,
       v.row_index,
       v.col_index,
       v.description
FROM demo,
     (VALUES (1, 'MEDIUM', 'DRONE', 1, 1, 'O nhan hang drone - hang 1 cot 1'),
             (2, 'MEDIUM', 'DRONE', 1, 2, 'O nhan hang drone - hang 1 cot 2'),
             (3, 'MEDIUM', 'DRONE', 1, 3, 'O nhan hang drone - hang 1 cot 3'),
             (4, 'MEDIUM', 'STANDARD', 2, 1, 'O thuong da dich vu'),
             (5, 'MEDIUM', 'STANDARD', 2, 2, 'O thuong da dich vu'),
             (6, 'MEDIUM', 'STANDARD', 2, 3, 'O thuong da dich vu'),
             (7, 'MEDIUM', 'STANDARD', 3, 1, 'O thuong da dich vu'),
             (8, 'MEDIUM', 'STANDARD', 3, 2, 'O thuong da dich vu'),
             (9, 'MEDIUM', 'STANDARD', 3, 3, 'O thuong da dich vu'),
             (10, 'XL', 'XL', 2, 0,
              'O vali 0.30x0.80x0.40 - khu ben trai')) AS v(box_number, size, cell_type, row_index, col_index, description)
WHERE NOT EXISTS (SELECT 1
                  FROM locker_schema.locker_boxes b
                  WHERE b.locker_id = demo.id);
