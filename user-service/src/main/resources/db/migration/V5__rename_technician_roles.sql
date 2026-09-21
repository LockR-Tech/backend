-- Đổi tên 2 role kỹ thuật viên cho đúng nghĩa:
--   TECHNICIAN  -> LOCKER_TECHNICIAN (kỹ thuật viên tủ: quản lý, bảo trì, vận hành tủ + IoT)
--   MAINTENANCE -> DRONE_TECHNICIAN  (kỹ thuật viên drone: quản lý, bảo trì, vận hành drone)
-- Cột `roles` là chuỗi role nối bằng dấu phẩy nên phải thay theo ranh giới từ
-- (`\m`, `\M`). Dấu gạch dưới là ký tự từ trong regex Postgres, nhờ vậy
-- 'TECHNICIAN' không khớp phần đuôi của 'LOCKER_TECHNICIAN'/'DRONE_TECHNICIAN'
-- — thứ tự 2 câu UPDATE không ảnh hưởng kết quả và chạy lại cũng không hỏng.

UPDATE user_schema.user_profiles
SET roles = regexp_replace(roles, '\mMAINTENANCE\M', 'DRONE_TECHNICIAN', 'g')
WHERE roles ~ '\mMAINTENANCE\M';

UPDATE user_schema.user_profiles
SET roles = regexp_replace(roles, '\mTECHNICIAN\M', 'LOCKER_TECHNICIAN', 'g')
WHERE roles ~ '\mTECHNICIAN\M';
