-- PA3 ops cleanup: xóa các database rỗng thừa trên deployment ĐÃ CHẠY.
-- (init-databases.sql chỉ chạy lúc khởi tạo container Postgres lần đầu; với DB
--  đã tồn tại phải chạy script này thủ công.)
--
-- Cách chạy (superuser postgres):
--   docker exec -i ll-ms-postgres psql -U postgres -f - < scripts/drop-legacy-databases.sql
--   (hoặc copy nội dung vào psql)
--
-- LƯU Ý: đảm bảo KHÔNG còn service nào kết nối tới 2 DB này (partner-service đã
-- gỡ; laundry-service bị skip). 2 DB này rỗng (không có migration/bảng).

-- partner_db: role PARTNER đã bỏ.
DROP
DATABASE IF EXISTS partner_db;
DROP
USER IF EXISTS partner_user;

-- laundry_db: laundry-service thiếu source, bị skip; DB rỗng.
DROP
DATABASE IF EXISTS laundry_db;
DROP
USER IF EXISTS laundry_user;

-- staff_db: staff-service đã bỏ hoàn toàn (PA3 đợt 2) — không flow nào dùng.
DROP
DATABASE IF EXISTS staff_db;
DROP
USER IF EXISTS staff_user;
