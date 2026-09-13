-- Script bổ sung dữ liệu cho account technical huynqbse180211@fpt.edu.vn
-- Chạy trên server (Azure VM) qua docker exec:
-- cat seed.sql | docker exec -i ll-ms-postgres psql -U postgres

\set
ON_ERROR_STOP on

\echo '1. Tìm user_id của huynqbse180211@fpt.edu.vn và gán quyền TECHNICIAN'
\connect user_db
DO $$
DECLARE
v_user_id BIGINT;
BEGIN
SELECT id
INTO v_user_id
FROM user_schema.user_profiles
WHERE email = 'huynqbse180211@fpt.edu.vn';

IF
v_user_id IS NULL THEN
        RAISE EXCEPTION 'Không tìm thấy user huynqbse180211@fpt.edu.vn!';
END IF;

UPDATE user_schema.user_profiles
SET roles = 'TECHNICIAN'
WHERE id = v_user_id;
END $$;

\echo
'2. Lấy user_id lưu vào bảng tạm để dùng chung'
CREATE
TEMP TABLE tmp_user AS
SELECT id AS user_id
FROM user_schema.user_profiles
WHERE email = 'huynqbse180211@fpt.edu.vn';

\echo
'3. Gán dữ liệu trong locker_db'
\connect locker_db
DO $$
DECLARE
v_user_id BIGINT;
BEGIN
    -- Không dùng dblink, ta sẽ lấy lại ID bằng cách tra trực tiếp email nếu chung DB, 
    -- nhưng khác DB thì temp table không share được qua \connect.
    -- Giải pháp: Lấy ID cố định? Không, ta có thể dùng bash script truyền vào, 
    -- Hoặc tạo extension dblink.
END $$;
