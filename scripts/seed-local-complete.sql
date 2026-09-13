-- =====================================================================
-- SEED LOCAL HOÀN CHỈNH — Smart Laundry Locker (microservices)
-- =====================================================================
-- Xoá sạch dữ liệu MỌI bảng nghiệp vụ trong 9 database rồi nạp lại một bộ
-- dữ liệu nhất quán, mọi bảng đều có dữ liệu và tham chiếu chéo khớp nhau.
--
-- CÁCH CHẠY (Postgres local của docker compose):
--   docker exec -i ll-ms-postgres psql -U postgres -v ON_ERROR_STOP=1 \
--     < scripts/seed-local-complete.sql
--
-- Yêu cầu: backend đã chạy ít nhất một lần để Flyway tạo xong schema.
-- KHÔNG đụng tới flyway_schema_history.
-- Chạy lại nhiều lần được (luôn TRUNCATE trước khi nạp).
--
-- 4 TÀI KHOẢN CHÍNH — mật khẩu đều là 12345678
--   id 1  ADMIN                        baohuy2k12k4@gmail.com               (web admin)
--   id 2  CUSTOMER                     nqbhuy2004nt@gmail.com               (mobile khách)
--   id 3  MAINTENANCE                  se180211nguyenquocbaohuy@gmail.com   (mobile bảo trì)
--   id 4  TECHNICIAN                   huynqbse180211@fpt.edu.vn            (mobile kỹ thuật/quản lý vận hành)
--
-- Role model chuẩn của hệ thống (JwtGatewayFilter.hasRequiredRole):
--   CUSTOMER · ADMIN (web console) · TECHNICIAN (bảo trì tủ + IoT) · MAINTENANCE (đội drone)
-- MANAGER/STAFF đã khai tử. Persona "Quản lý vận hành" dùng TECHNICIAN.
-- =====================================================================


-- =====================================================================
-- 1) user_db — hồ sơ người dùng
-- =====================================================================
\connect user_db

TRUNCATE TABLE user_schema.user_profiles RESTART IDENTITY CASCADE;

INSERT INTO user_schema.user_profiles
(id, email, phone_number, first_name, last_name, birthday, image_url, status, roles, created_at, updated_at)
VALUES
(1,  'baohuy2k12k4@gmail.com',             '0901000001', 'Bảo Huy',  'Nguyễn Quốc', DATE '1999-03-12', 'https://i.pravatar.cc/300?img=12', 'ACTIVE', 'ADMIN',                        NOW() - INTERVAL '400 days', NOW() - INTERVAL '2 days'),
(2,  'nqbhuy2004nt@gmail.com',             '0901000002', 'Quốc Bảo', 'Nguyễn',      DATE '2004-04-04', 'https://i.pravatar.cc/300?img=33', 'ACTIVE', 'CUSTOMER',                     NOW() - INTERVAL '320 days', NOW() - INTERVAL '1 day'),
(3,  'se180211nguyenquocbaohuy@gmail.com', '0901000003', 'Bảo Huy',  'Nguyễn',      DATE '2001-08-21', 'https://i.pravatar.cc/300?img=51', 'ACTIVE', 'MAINTENANCE',                  NOW() - INTERVAL '300 days', NOW() - INTERVAL '3 days'),
(4,  'huynqbse180211@fpt.edu.vn',          '0901000004', 'Bảo Huy',  'Huỳnh',       DATE '2000-11-02', 'https://i.pravatar.cc/300?img=68', 'ACTIVE', 'TECHNICIAN',                   NOW() - INTERVAL '300 days', NOW() - INTERVAL '4 days'),
(5,  'minhanh.tran@gmail.com',             '0901000005', 'Minh Anh', 'Trần',        DATE '1998-01-15', NULL, 'ACTIVE',   'CUSTOMER',            NOW() - INTERVAL '210 days', NOW() - INTERVAL '9 days'),
(6,  'yenvi.le@gmail.com',                 '0901000006', 'Yến Vi',   'Lê Thị',      DATE '2002-06-30', NULL, 'ACTIVE',   'CUSTOMER',            NOW() - INTERVAL '180 days', NOW() - INTERVAL '7 days'),
(7,  'khang.nguyen@gmail.com',             '0901000007', 'Văn Khang','Nguyễn',      DATE '1997-09-09', NULL, 'ACTIVE',   'CUSTOMER',            NOW() - INTERVAL '150 days', NOW() - INTERVAL '5 days'),
(8,  'thuha.pham@gmail.com',               '0901000008', 'Thu Hà',   'Phạm',        DATE '2003-02-18', NULL, 'ACTIVE',   'CUSTOMER',            NOW() - INTERVAL '120 days', NOW() - INTERVAL '6 days'),
(9,  'quocbao.do@gmail.com',               '0901000009', 'Quốc Bảo', 'Đỗ',          DATE '1996-12-25', NULL, 'INACTIVE', 'CUSTOMER',            NOW() - INTERVAL '110 days', NOW() - INTERVAL '40 days'),
(10, 'hoangnam.vu@gmail.com',              '0901000010', 'Hoàng Nam','Vũ',          DATE '2001-05-05', NULL, 'ACTIVE',   'CUSTOMER',            NOW() - INTERVAL '90 days',  NOW() - INTERVAL '3 days'),
(11, 'thaibinh.truong@gmail.com',          '0901000011', 'Thái Bình','Trương Nguyễn', DATE '1999-07-07', NULL, 'ACTIVE', 'TECHNICIAN',           NOW() - INTERVAL '260 days', NOW() - INTERVAL '8 days'),
(12, 'baochau.ngo@gmail.com',              '0901000012', 'Bảo Châu', 'Ngô',         DATE '2000-10-10', NULL, 'ACTIVE',   'CUSTOMER',            NOW() - INTERVAL '60 days',  NOW() - INTERVAL '2 days');

SELECT setval('user_schema.user_profiles_id_seq', 12, true);


-- =====================================================================
-- 2) auth_db — tài khoản đăng nhập, refresh token, OTP, social login
-- =====================================================================
\connect auth_db

TRUNCATE TABLE auth_schema.social_identities, auth_schema.refresh_tokens,
               auth_schema.email_otps, auth_schema.auth_accounts
  RESTART IDENTITY CASCADE;

-- bcrypt(12345678, strength 10)
INSERT INTO auth_schema.auth_accounts
(id, user_id, email, phone_number, password_hash, auth_provider, email_verified, phone_verified, status, last_login_at, created_at, updated_at)
VALUES
(1,  1,  'baohuy2k12k4@gmail.com',             '0901000001', '$2a$10$S2LjQzym5drWNMKOdCpbWuZIKwJONRrabq54MiQnweVSvRlHMvf5O', 'LOCAL',    TRUE,  TRUE,  'ACTIVE',   NOW() - INTERVAL '2 hours', NOW() - INTERVAL '400 days', NOW() - INTERVAL '2 hours'),
(2,  2,  'nqbhuy2004nt@gmail.com',             '0901000002', '$2a$10$S2LjQzym5drWNMKOdCpbWuZIKwJONRrabq54MiQnweVSvRlHMvf5O', 'GOOGLE',   TRUE,  TRUE,  'ACTIVE',   NOW() - INTERVAL '5 hours', NOW() - INTERVAL '320 days', NOW() - INTERVAL '5 hours'),
(3,  3,  'se180211nguyenquocbaohuy@gmail.com', '0901000003', '$2a$10$S2LjQzym5drWNMKOdCpbWuZIKwJONRrabq54MiQnweVSvRlHMvf5O', 'LOCAL',    TRUE,  TRUE,  'ACTIVE',   NOW() - INTERVAL '1 day',   NOW() - INTERVAL '300 days', NOW() - INTERVAL '1 day'),
(4,  4,  'huynqbse180211@fpt.edu.vn',          '0901000004', '$2a$10$S2LjQzym5drWNMKOdCpbWuZIKwJONRrabq54MiQnweVSvRlHMvf5O', 'LOCAL',    TRUE,  TRUE,  'ACTIVE',   NOW() - INTERVAL '8 hours', NOW() - INTERVAL '300 days', NOW() - INTERVAL '8 hours'),
(5,  5,  'minhanh.tran@gmail.com',             '0901000005', '$2a$10$S2LjQzym5drWNMKOdCpbWuZIKwJONRrabq54MiQnweVSvRlHMvf5O', 'LOCAL',    TRUE,  FALSE, 'ACTIVE',   NOW() - INTERVAL '3 days',  NOW() - INTERVAL '210 days', NOW() - INTERVAL '3 days'),
(6,  6,  'yenvi.le@gmail.com',                 '0901000006', '$2a$10$S2LjQzym5drWNMKOdCpbWuZIKwJONRrabq54MiQnweVSvRlHMvf5O', 'FACEBOOK', TRUE,  FALSE, 'ACTIVE',   NOW() - INTERVAL '4 days',  NOW() - INTERVAL '180 days', NOW() - INTERVAL '4 days'),
(7,  7,  'khang.nguyen@gmail.com',             '0901000007', '$2a$10$S2LjQzym5drWNMKOdCpbWuZIKwJONRrabq54MiQnweVSvRlHMvf5O', 'LOCAL',    TRUE,  TRUE,  'ACTIVE',   NOW() - INTERVAL '6 days',  NOW() - INTERVAL '150 days', NOW() - INTERVAL '6 days'),
(8,  8,  'thuha.pham@gmail.com',               '0901000008', '$2a$10$S2LjQzym5drWNMKOdCpbWuZIKwJONRrabq54MiQnweVSvRlHMvf5O', 'LOCAL',    FALSE, TRUE,  'ACTIVE',   NOW() - INTERVAL '9 days',  NOW() - INTERVAL '120 days', NOW() - INTERVAL '9 days'),
(9,  9,  'quocbao.do@gmail.com',               '0901000009', '$2a$10$S2LjQzym5drWNMKOdCpbWuZIKwJONRrabq54MiQnweVSvRlHMvf5O', 'LOCAL',    TRUE,  FALSE, 'INACTIVE', NOW() - INTERVAL '40 days', NOW() - INTERVAL '110 days', NOW() - INTERVAL '40 days'),
(10, 10, 'hoangnam.vu@gmail.com',              '0901000010', '$2a$10$S2LjQzym5drWNMKOdCpbWuZIKwJONRrabq54MiQnweVSvRlHMvf5O', 'GOOGLE',   TRUE,  FALSE, 'ACTIVE',   NOW() - INTERVAL '2 days',  NOW() - INTERVAL '90 days',  NOW() - INTERVAL '2 days'),
(11, 11, 'thaibinh.truong@gmail.com',          '0901000011', '$2a$10$S2LjQzym5drWNMKOdCpbWuZIKwJONRrabq54MiQnweVSvRlHMvf5O', 'LOCAL',    TRUE,  TRUE,  'ACTIVE',   NOW() - INTERVAL '1 day',   NOW() - INTERVAL '260 days', NOW() - INTERVAL '1 day'),
(12, 12, 'baochau.ngo@gmail.com',              '0901000012', '$2a$10$S2LjQzym5drWNMKOdCpbWuZIKwJONRrabq54MiQnweVSvRlHMvf5O', 'LOCAL',    TRUE,  FALSE, 'ACTIVE',   NOW() - INTERVAL '7 days',  NOW() - INTERVAL '60 days',  NOW() - INTERVAL '7 days');

SELECT setval('auth_schema.auth_accounts_id_seq', 12, true);

INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, revoked, created_at)
VALUES
(1, 1, 'rt-hash-admin-active-0001',       NOW() + INTERVAL '25 days', FALSE, NOW() - INTERVAL '2 hours'),
(2, 2, 'rt-hash-customer-active-0002',    NOW() + INTERVAL '27 days', FALSE, NOW() - INTERVAL '5 hours'),
(3, 3, 'rt-hash-maintenance-active-0003', NOW() + INTERVAL '29 days', FALSE, NOW() - INTERVAL '1 day'),
(4, 4, 'rt-hash-manager-active-0004',     NOW() + INTERVAL '29 days', FALSE, NOW() - INTERVAL '8 hours'),
(5, 2, 'rt-hash-customer-revoked-0005',   NOW() - INTERVAL '1 day',   TRUE,  NOW() - INTERVAL '31 days'),
(6, 7, 'rt-hash-khang-active-0006',       NOW() + INTERVAL '24 days', FALSE, NOW() - INTERVAL '6 days');

SELECT setval('auth_schema.refresh_tokens_id_seq', 6, true);

INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, used, created_at)
VALUES
(1, 'nqbhuy2004nt@gmail.com',    'otp-hash-used-0001',    'LOGIN',            NOW() - INTERVAL '5 hours',  TRUE,  NOW() - INTERVAL '5 hours'),
(2, 'baohuy2k12k4@gmail.com',    'otp-hash-used-0002',    'LOGIN',            NOW() - INTERVAL '2 hours',  TRUE,  NOW() - INTERVAL '2 hours'),
(3, 'thuha.pham@gmail.com',      'otp-hash-pending-0003', 'EMAIL_VERIFY',     NOW() + INTERVAL '9 minutes', FALSE, NOW() - INTERVAL '1 minute'),
(4, 'minhanh.tran@gmail.com',    'otp-hash-expired-0004', 'RESET_PASSWORD',   NOW() - INTERVAL '2 days',   FALSE, NOW() - INTERVAL '2 days'),
(5, 'baochau.ngo@gmail.com',     'otp-hash-used-0005',    'EMAIL_REGISTRATION', NOW() - INTERVAL '7 days', TRUE,  NOW() - INTERVAL '7 days');

SELECT setval('auth_schema.email_otps_id_seq', 5, true);

INSERT INTO auth_schema.social_identities (id, account_id, provider, provider_user_id, created_at)
VALUES
(1, 2,  'GOOGLE',   'google-uid-nqbhuy-8842013', NOW() - INTERVAL '320 days'),
(2, 6,  'FACEBOOK', 'fb-uid-yenvi-6610224',      NOW() - INTERVAL '180 days'),
(3, 10, 'GOOGLE',   'google-uid-hoangnam-771903', NOW() - INTERVAL '90 days');

SELECT setval('auth_schema.social_identities_id_seq', 3, true);


-- =====================================================================
-- 3) store_db — điểm đặt tủ
-- =====================================================================
\connect store_db

TRUNCATE TABLE store_schema.stores RESTART IDENTITY CASCADE;

INSERT INTO store_schema.stores
(id, name, contact_phone, address, latitude, longitude, image, description, is_active, status, created_at, updated_at)
VALUES
(1, 'FPT University HCMC',  '02873005588', 'Lô E2a-7, Đường D1, Khu CNC, TP. Thủ Đức, TP.HCM', 10.841100, 106.809700, 'https://picsum.photos/seed/fptu/800/400',     'Điểm đặt tủ demo capstone, có bãi đáp drone trên nóc', TRUE,  'ACTIVE',   NOW() - INTERVAL '400 days', NOW() - INTERVAL '30 days'),
(2, 'Vinhomes Grand Park',  '02873005589', 'Nguyễn Xiển, Long Thạnh Mỹ, TP. Thủ Đức, TP.HCM',  10.842900, 106.836100, 'https://picsum.photos/seed/vgp/800/400',      'Sảnh block S5.02, hoạt động 24/7',                     TRUE,  'ACTIVE',   NOW() - INTERVAL '350 days', NOW() - INTERVAL '20 days'),
(3, 'Landmark 81',          '02873005590', '720A Điện Biên Phủ, Bình Thạnh, TP.HCM',           10.794700, 106.721900, 'https://picsum.photos/seed/lm81/800/400',     'Tầng hầm B1, cạnh khu gửi xe',                          TRUE,  'ACTIVE',   NOW() - INTERVAL '300 days', NOW() - INTERVAL '15 days'),
(4, 'Aeon Mall Tân Phú',    '02873005591', '30 Bờ Bao Tân Thắng, Sơn Kỳ, Tân Phú, TP.HCM',     10.801400, 106.617500, 'https://picsum.photos/seed/aeon/800/400',     'Cổng B, gần khu ẩm thực — đang tạm ngưng để nâng cấp',  FALSE, 'INACTIVE', NOW() - INTERVAL '260 days', NOW() - INTERVAL '5 days');

SELECT setval('store_schema.stores_id_seq', 4, true);


-- =====================================================================
-- 4) locker_db — tủ, ô tủ, sự cố, bảo trì, drone
-- =====================================================================
\connect locker_db

TRUNCATE TABLE locker_schema.drone_delivery_requests, locker_schema.drone_maintenance_logs,
               locker_schema.locker_report_ratings, locker_schema.repair_logs,
               locker_schema.maintenance_schedules, locker_schema.locker_reports,
               locker_schema.drone_units, locker_schema.locker_boxes, locker_schema.lockers
  RESTART IDENTITY CASCADE;

INSERT INTO locker_schema.lockers
(id, store_id, code, name, status, address, latitude, longitude, description, landing_pad, landing_marker_id, landing_pad_status, created_at, updated_at)
VALUES
(1, 1, 'CAB-DEMO-01', 'Tủ demo capstone 3x3 + vali', 'ACTIVE',      'FPT University HCMC',                              10.841100, 106.809700, 'Tủ demo theo bản vẽ 1.5x1.2x0.5m, bãi đáp drone trên nóc, hàng 1 dành cho drone', TRUE,  'ARUCO-23', 'OK',          NOW() - INTERVAL '400 days', NOW() - INTERVAL '1 day'),
(2, 2, 'CAB-VGP-01',  'Tủ Vinhomes Grand Park S5.02','ACTIVE',      'Sảnh block S5.02, Vinhomes Grand Park',            10.842900, 106.836100, 'Tủ 9 ô, có bãi đáp drone',                                                        TRUE,  'ARUCO-41', 'OK',          NOW() - INTERVAL '350 days', NOW() - INTERVAL '2 days'),
(3, 3, 'CAB-LM81-01', 'Tủ Landmark 81 B1',           'ACTIVE',      'Tầng hầm B1, Landmark 81',                         10.794700, 106.721900, 'Tủ 9 ô trong hầm, không có bãi đáp drone',                                        FALSE, NULL,       'OK',          NOW() - INTERVAL '300 days', NOW() - INTERVAL '3 days'),
(4, 4, 'CAB-AEON-01', 'Tủ Aeon Tân Phú cổng B',      'MAINTENANCE', 'Cổng B, Aeon Mall Tân Phú',                        10.801400, 106.617500, 'Đang nâng cấp bo mạch RS485, tạm ngưng nhận đơn mới',                              TRUE,  'ARUCO-77', 'MAINTENANCE', NOW() - INTERVAL '260 days', NOW() - INTERVAL '5 days');

SELECT setval('locker_schema.lockers_id_seq', 4, true);

-- Ô tủ: 10 ô cho tủ demo (đúng bản vẽ), 9 ô cho mỗi tủ còn lại.
INSERT INTO locker_schema.locker_boxes
(id, locker_id, box_number, size, is_active, description, status, cell_type, row_index, col_index, fault_reason, reserved_until, created_at, updated_at)
VALUES
-- Tủ 1 — CAB-DEMO-01
(1,  1, 1,  'MEDIUM', TRUE, 'Ô nhận hàng drone - hàng 1 cột 1', 'RESERVED',  'DRONE',    1, 1, NULL, NOW() + INTERVAL '3 hours', NOW() - INTERVAL '400 days', NOW() - INTERVAL '2 hours'),
(2,  1, 2,  'MEDIUM', TRUE, 'Ô nhận hàng drone - hàng 1 cột 2', 'AVAILABLE', 'DRONE',    1, 2, NULL, NULL, NOW() - INTERVAL '400 days', NULL),
(3,  1, 3,  'MEDIUM', TRUE, 'Ô nhận hàng drone - hàng 1 cột 3', 'AVAILABLE', 'DRONE',    1, 3, NULL, NULL, NOW() - INTERVAL '400 days', NULL),
(4,  1, 4,  'MEDIUM', TRUE, 'Ô thường đa dịch vụ',              'OCCUPIED',  'STANDARD', 2, 1, NULL, NULL, NOW() - INTERVAL '400 days', NOW() - INTERVAL '6 hours'),
(5,  1, 5,  'MEDIUM', TRUE, 'Ô thường đa dịch vụ',              'AVAILABLE', 'STANDARD', 2, 2, NULL, NULL, NOW() - INTERVAL '400 days', NULL),
(6,  1, 6,  'MEDIUM', TRUE, 'Ô thường đa dịch vụ',              'AVAILABLE', 'STANDARD', 2, 3, NULL, NULL, NOW() - INTERVAL '400 days', NULL),
(7,  1, 7,  'MEDIUM', TRUE, 'Ô thường đa dịch vụ',              'OCCUPIED',  'STANDARD', 3, 1, NULL, NULL, NOW() - INTERVAL '400 days', NOW() - INTERVAL '20 hours'),
(8,  1, 8,  'MEDIUM', TRUE, 'Ô thường đa dịch vụ',              'FAULT',     'STANDARD', 3, 2, 'Cửa không đóng khít, cảm biến báo hở', NULL, NOW() - INTERVAL '400 days', NOW() - INTERVAL '2 days'),
(9,  1, 9,  'MEDIUM', TRUE, 'Ô thường đa dịch vụ',              'AVAILABLE', 'STANDARD', 3, 3, NULL, NULL, NOW() - INTERVAL '400 days', NULL),
(10, 1, 10, 'XL',     TRUE, 'Ô vali 0.30x0.80x0.40 - khu trái', 'AVAILABLE', 'XL',       2, 0, NULL, NULL, NOW() - INTERVAL '400 days', NULL),
-- Tủ 2 — CAB-VGP-01
(11, 2, 1, 'MEDIUM', TRUE, 'Ô drone hàng 1', 'RESERVED',  'DRONE',    1, 1, NULL, NOW() + INTERVAL '1 hour', NOW() - INTERVAL '350 days', NOW() - INTERVAL '30 minutes'),
(12, 2, 2, 'MEDIUM', TRUE, 'Ô drone hàng 1', 'OCCUPIED',  'DRONE',    1, 2, NULL, NULL, NOW() - INTERVAL '350 days', NOW() - INTERVAL '10 hours'),
(13, 2, 3, 'MEDIUM', TRUE, 'Ô thường',       'AVAILABLE', 'STANDARD', 2, 1, NULL, NULL, NOW() - INTERVAL '350 days', NULL),
(14, 2, 4, 'MEDIUM', TRUE, 'Ô thường',       'AVAILABLE', 'STANDARD', 2, 2, NULL, NULL, NOW() - INTERVAL '350 days', NULL),
(15, 2, 5, 'MEDIUM', TRUE, 'Ô thường',       'AVAILABLE', 'STANDARD', 2, 3, NULL, NULL, NOW() - INTERVAL '350 days', NULL),
(16, 2, 6, 'MEDIUM', TRUE, 'Ô thường',       'AVAILABLE', 'STANDARD', 3, 1, NULL, NULL, NOW() - INTERVAL '350 days', NULL),
(17, 2, 7, 'MEDIUM', TRUE, 'Ô thường',       'AVAILABLE', 'STANDARD', 3, 2, NULL, NULL, NOW() - INTERVAL '350 days', NULL),
(18, 2, 8, 'MEDIUM', TRUE, 'Ô thường',       'AVAILABLE', 'STANDARD', 3, 3, NULL, NULL, NOW() - INTERVAL '350 days', NULL),
(19, 2, 9, 'XL',     TRUE, 'Ô vali',         'AVAILABLE', 'XL',       2, 0, NULL, NULL, NOW() - INTERVAL '350 days', NULL),
-- Tủ 3 — CAB-LM81-01
(20, 3, 1, 'MEDIUM', TRUE,  'Ô thường', 'AVAILABLE', 'STANDARD', 1, 1, NULL, NULL, NOW() - INTERVAL '300 days', NULL),
(21, 3, 2, 'MEDIUM', TRUE,  'Ô thường', 'RESERVED',  'STANDARD', 1, 2, NULL, NOW() + INTERVAL '45 minutes', NOW() - INTERVAL '300 days', NOW() - INTERVAL '15 minutes'),
(22, 3, 3, 'MEDIUM', TRUE,  'Ô thường', 'AVAILABLE', 'STANDARD', 1, 3, NULL, NULL, NOW() - INTERVAL '300 days', NULL),
(23, 3, 4, 'MEDIUM', TRUE,  'Ô thường', 'AVAILABLE', 'STANDARD', 2, 1, NULL, NULL, NOW() - INTERVAL '300 days', NULL),
(24, 3, 5, 'MEDIUM', TRUE,  'Ô thường', 'AVAILABLE', 'STANDARD', 2, 2, NULL, NULL, NOW() - INTERVAL '300 days', NULL),
(25, 3, 6, 'MEDIUM', FALSE, 'Ô thường', 'FAULT',     'STANDARD', 2, 3, 'Khoá điện từ kẹt, đã ngưng sử dụng', NULL, NOW() - INTERVAL '300 days', NOW() - INTERVAL '8 days'),
(26, 3, 7, 'MEDIUM', TRUE,  'Ô thường', 'AVAILABLE', 'STANDARD', 3, 1, NULL, NULL, NOW() - INTERVAL '300 days', NULL),
(27, 3, 8, 'MEDIUM', TRUE,  'Ô thường', 'AVAILABLE', 'STANDARD', 3, 2, NULL, NULL, NOW() - INTERVAL '300 days', NULL),
(28, 3, 9, 'XL',     TRUE,  'Ô vali',   'AVAILABLE', 'XL',       3, 0, NULL, NULL, NOW() - INTERVAL '300 days', NULL),
-- Tủ 4 — CAB-AEON-01 (đang bảo trì)
(29, 4, 1, 'MEDIUM', TRUE,  'Ô drone',  'AVAILABLE', 'DRONE',    1, 1, NULL, NULL, NOW() - INTERVAL '260 days', NULL),
(30, 4, 2, 'MEDIUM', TRUE,  'Ô thường', 'OCCUPIED',  'STANDARD', 1, 2, NULL, NULL, NOW() - INTERVAL '260 days', NOW() - INTERVAL '2 days'),
(31, 4, 3, 'MEDIUM', TRUE,  'Ô thường', 'AVAILABLE', 'STANDARD', 1, 3, NULL, NULL, NOW() - INTERVAL '260 days', NULL),
(32, 4, 4, 'MEDIUM', TRUE,  'Ô thường', 'AVAILABLE', 'STANDARD', 2, 1, NULL, NULL, NOW() - INTERVAL '260 days', NULL),
(33, 4, 5, 'MEDIUM', FALSE, 'Ô thường', 'FAULT',     'STANDARD', 2, 2, 'Bo mạch RS485 lỗi, chờ thay', NULL, NOW() - INTERVAL '260 days', NOW() - INTERVAL '5 days'),
(34, 4, 6, 'MEDIUM', TRUE,  'Ô thường', 'AVAILABLE', 'STANDARD', 2, 3, NULL, NULL, NOW() - INTERVAL '260 days', NULL),
(35, 4, 7, 'MEDIUM', TRUE,  'Ô thường', 'AVAILABLE', 'STANDARD', 3, 1, NULL, NULL, NOW() - INTERVAL '260 days', NULL),
(36, 4, 8, 'MEDIUM', TRUE,  'Ô thường', 'AVAILABLE', 'STANDARD', 3, 2, NULL, NULL, NOW() - INTERVAL '260 days', NULL),
(37, 4, 9, 'XL',     TRUE,  'Ô vali',   'AVAILABLE', 'XL',       3, 0, NULL, NULL, NOW() - INTERVAL '260 days', NULL);

SELECT setval('locker_schema.locker_boxes_id_seq', 37, true);

-- Đội drone: 3 con ở tủ demo, 2 ở VGP, 1 ở Aeon (ngưng hoạt động).
INSERT INTO locker_schema.drone_units
(id, locker_id, code, status, battery_percent, fault_reason, assigned_technician_id, last_charged_at, active, created_at, updated_at)
VALUES
(1, 1, 'DRONE-01', 'IDLE',     92, NULL,                                  3,    NOW() - INTERVAL '6 hours',  TRUE,  NOW() - INTERVAL '200 days', NOW() - INTERVAL '6 hours'),
(2, 1, 'DRONE-02', 'CHARGING', 41, NULL,                                  3,    NOW() - INTERVAL '30 minutes', TRUE, NOW() - INTERVAL '200 days', NOW() - INTERVAL '30 minutes'),
(3, 1, 'DRONE-03', 'FAULT',    15, 'Cảm biến GPS lỗi, cần kiểm tra lại',  3,    NOW() - INTERVAL '3 days',   TRUE,  NOW() - INTERVAL '200 days', NOW() - INTERVAL '2 days'),
(4, 2, 'DRONE-04', 'IDLE',     88, NULL,                                  11,   NOW() - INTERVAL '10 hours', TRUE,  NOW() - INTERVAL '150 days', NOW() - INTERVAL '10 hours'),
(5, 2, 'DRONE-05', 'FLYING',   67, NULL,                                  11,   NOW() - INTERVAL '2 hours',  TRUE,  NOW() - INTERVAL '150 days', NOW() - INTERVAL '30 minutes'),
(6, 4, 'DRONE-06', 'FAULT',     8, 'Rơi khi hạ cánh, gãy càng - chờ thay', 4,   NOW() - INTERVAL '20 days',  FALSE, NOW() - INTERVAL '120 days', NOW() - INTERVAL '18 days');

SELECT setval('locker_schema.drone_units_id_seq', 6, true);

-- Phiếu sự cố: 3 phiếu ô tủ + 2 phiếu drone (box_id NULL) + 3 phiếu đã xử lý xong.
INSERT INTO locker_schema.locker_reports
(id, locker_id, user_id, title, description, status, resolved_by_user_id, resolved_at, box_id, assigned_to_user_id, assigned_at, drone_unit_id, created_at, updated_at)
VALUES
(1, 1, 2,  'Ô số 8 không đóng được cửa',        'Em gửi đồ xong đóng cửa nhưng cảm biến vẫn báo hở, tủ không khoá lại.', 'IN_PROGRESS', NULL, NULL, 8,    3,  NOW() - INTERVAL '2 days',  NULL, NOW() - INTERVAL '2 days',  NOW() - INTERVAL '2 days'),
(2, 3, 5,  'Ô số 6 kẹt khoá, không mở ra được', 'Nhập PIN đúng, đèn xanh nhưng cửa không bật. Đồ vẫn kẹt bên trong.',    'IN_PROGRESS', NULL, NULL, 25,   11, NOW() - INTERVAL '8 days',  NULL, NOW() - INTERVAL '8 days',  NOW() - INTERVAL '7 days'),
(3, 4, 7,  'Toàn bộ tủ không phản hồi',         'Màn hình kiosk đen, quét QR không ăn. Cả tủ như mất điện.',              'RESOLVED',    4,    NOW() - INTERVAL '4 days', NULL, 4,  NOW() - INTERVAL '6 days',  NULL, NOW() - INTERVAL '6 days',  NOW() - INTERVAL '4 days'),
(4, 1, 6,  'Ô số 5 bẩn, có mùi',                'Ô có vết nước và mùi ẩm mốc, đề nghị vệ sinh.',                          'RESOLVED',    3,    NOW() - INTERVAL '11 days', 5,   3,  NOW() - INTERVAL '12 days', NULL, NOW() - INTERVAL '12 days', NOW() - INTERVAL '11 days'),
(5, 1, 3,  'DRONE-03 báo lỗi GPS',              'Drone không giữ được vị trí khi hover, log báo GPS glitch liên tục.',    'OPEN',        NULL, NULL, NULL, 3,  NOW() - INTERVAL '2 days',  3,    NOW() - INTERVAL '2 days',  NOW() - INTERVAL '2 days'),
(6, 4, 4,  'DRONE-06 rơi khi hạ cánh',          'Drone mất thăng bằng ở độ cao 1m, gãy càng trái. Đã ngưng hoạt động.',   'IN_PROGRESS', NULL, NULL, NULL, 4,  NOW() - INTERVAL '18 days', 6,    NOW() - INTERVAL '18 days', NOW() - INTERVAL '18 days'),
(7, 2, 8,  'Kiosk quét QR chậm',                'Quét mã mất 5-6 giây mới nhận, thao tác rất lâu.',                       'RESOLVED',    11,   NOW() - INTERVAL '20 days', NULL, 11, NOW() - INTERVAL '22 days', NULL, NOW() - INTERVAL '22 days', NOW() - INTERVAL '20 days'),
(8, 2, 10, 'Đèn LED ô số 4 không sáng',         'Ô mở bình thường nhưng đèn báo không lên, tối khó nhìn.',                'OPEN',        NULL, NULL, 14,   NULL, NULL,                     NULL, NOW() - INTERVAL '1 day',   NOW() - INTERVAL '1 day');

SELECT setval('locker_schema.locker_reports_id_seq', 8, true);

INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES
(1,  1, 3,  'Đã tới hiện trường, xác nhận bản lề cửa bị vênh.',                   NOW() - INTERVAL '2 days'),
(2,  1, 3,  'Đã siết lại bản lề, còn lệch nhẹ. Chờ linh kiện thay cảm biến từ.',  NOW() - INTERVAL '1 day'),
(3,  2, 11, 'Kiểm tra khoá điện từ, cuộn dây có dấu hiệu cháy.',                  NOW() - INTERVAL '8 days'),
(4,  2, 11, 'Đã mở cửa bằng khoá cơ, trả đồ cho khách. Ô tạm ngưng sử dụng.',     NOW() - INTERVAL '7 days'),
(5,  3, 4,  'Kiểm tra nguồn: aptomat khu vực bị nhảy.',                           NOW() - INTERVAL '6 days'),
(6,  3, 4,  'Đã bật lại aptomat và kiểm tra toàn bộ 9 ô, hoạt động bình thường.', NOW() - INTERVAL '4 days'),
(7,  4, 3,  'Đã vệ sinh, khử mùi bằng dung dịch chuyên dụng.',                    NOW() - INTERVAL '11 days'),
(8,  5, 3,  'Đã tháo module GPS, gửi đi kiểm tra.',                               NOW() - INTERVAL '2 days'),
(9,  6, 4,  'Đã thu hồi drone về kho, lập biên bản hư hỏng.',                     NOW() - INTERVAL '18 days'),
(10, 6, 11, 'Đã đặt càng thay thế, dự kiến 2 tuần về hàng.',                      NOW() - INTERVAL '15 days'),
(11, 7, 11, 'Cập nhật firmware đầu đọc QR lên bản 2.3.1.',                        NOW() - INTERVAL '21 days'),
(12, 7, 11, 'Quét thử 20 lần, thời gian nhận trung bình 0.8s. Đóng phiếu.',       NOW() - INTERVAL '20 days');

SELECT setval('locker_schema.repair_logs_id_seq', 12, true);

INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES
(1, 3, 7,  5, 'Xử lý nhanh, hôm sau là dùng lại được.',            NOW() - INTERVAL '4 days',  NULL),
(2, 4, 6,  4, 'Ô sạch hơn hẳn, cảm ơn đội bảo trì.',               NOW() - INTERVAL '11 days', NULL),
(3, 7, 8,  5, 'Giờ quét mã nhanh, không phải đứng chờ nữa.',       NOW() - INTERVAL '20 days', NULL);

SELECT setval('locker_schema.locker_report_ratings_id_seq', 3, true);

INSERT INTO locker_schema.maintenance_schedules
(id, locker_id, title, interval_days, last_done_at, next_due_at, active, drone_unit_id, created_at, updated_at)
VALUES
(1, 1,    'Vệ sinh định kỳ tủ demo',            30, NOW() - INTERVAL '25 days', NOW() + INTERVAL '5 days',   TRUE,  NULL, NOW() - INTERVAL '200 days', NOW() - INTERVAL '25 days'),
(2, 1,    'Kiểm tra bãi đáp drone + marker',    14, NOW() - INTERVAL '13 days', NOW() + INTERVAL '1 day',    TRUE,  NULL, NOW() - INTERVAL '200 days', NOW() - INTERVAL '13 days'),
(3, 2,    'Vệ sinh + kiểm tra khoá điện từ',    30, NOW() - INTERVAL '32 days', NOW() - INTERVAL '2 days',   TRUE,  NULL, NOW() - INTERVAL '180 days', NOW() - INTERVAL '32 days'),
(4, 3,    'Kiểm tra nguồn và UPS hầm B1',       60, NOW() - INTERVAL '58 days', NOW() + INTERVAL '2 days',   TRUE,  NULL, NOW() - INTERVAL '150 days', NOW() - INTERVAL '58 days'),
(5, NULL, 'Cân bằng cánh + hiệu chuẩn DRONE-01', 21, NOW() - INTERVAL '18 days', NOW() + INTERVAL '3 days',  TRUE,  1,    NOW() - INTERVAL '120 days', NOW() - INTERVAL '18 days'),
(6, NULL, 'Kiểm tra pin DRONE-04',              21, NOW() - INTERVAL '24 days', NOW() - INTERVAL '3 days',   TRUE,  4,    NOW() - INTERVAL '100 days', NOW() - INTERVAL '24 days'),
(7, 4,    'Thay bo mạch RS485 (tạm dừng)',      90, NULL,                        NOW() + INTERVAL '20 days', FALSE, NULL, NOW() - INTERVAL '60 days',  NOW() - INTERVAL '5 days');

SELECT setval('locker_schema.maintenance_schedules_id_seq', 7, true);

INSERT INTO locker_schema.drone_maintenance_logs (id, drone_unit_id, actor_user_id, note, created_at)
VALUES
(1, 1, 3,  'Hiệu chuẩn la bàn, bay thử 5 phút — ổn định.',            NOW() - INTERVAL '18 days'),
(2, 1, 3,  'Sạc đầy, đưa về trạng thái IDLE chờ nhiệm vụ.',           NOW() - INTERVAL '6 hours'),
(3, 2, 3,  'Cắm sạc, pin 41%, ước tính đầy sau 45 phút.',             NOW() - INTERVAL '30 minutes'),
(4, 3, 3,  'Phát hiện GPS glitch, chuyển trạng thái FAULT.',          NOW() - INTERVAL '2 days'),
(5, 3, 11, 'Đã tháo module GPS gửi bảo hành.',                        NOW() - INTERVAL '1 day'),
(6, 4, 11, 'Kiểm tra cánh và pin trước ca, đạt.',                     NOW() - INTERVAL '10 hours'),
(7, 5, 11, 'Xuất phát giao đơn ORD-DRN-0021.',                        NOW() - INTERVAL '30 minutes'),
(8, 6, 4,  'Lập biên bản hư hỏng sau sự cố rơi, ngưng hoạt động.',    NOW() - INTERVAL '18 days');

SELECT setval('locker_schema.drone_maintenance_logs_id_seq', 8, true);

-- Yêu cầu giao drone do khách tạo (hàng đợi của đội bay)
INSERT INTO locker_schema.drone_delivery_requests
(id, locker_id, box_id, box_number, requester_user_id, receiver_phone, description, status, drone_unit_id, dispatched_by, created_at, updated_at)
VALUES
(1, 1, 1,  1, 2,  '0901000005', 'Giao hộp tài liệu cho bạn Minh Anh',        'PENDING',    NULL, NULL, NOW() - INTERVAL '3 hours',  NOW() - INTERVAL '3 hours'),
(2, 2, 11, 1, 5,  '0901000002', 'Giao sạc dự phòng sang toà S5.02',          'DISPATCHED', 5,    3,    NOW() - INTERVAL '2 hours',  NOW() - INTERVAL '30 minutes'),
(3, 1, 2,  2, 6,  '0901000007', 'Gửi chìa khoá phòng',                        'DELIVERED',  1,    3,    NOW() - INTERVAL '3 days',   NOW() - INTERVAL '3 days'),
(4, 2, 12, 2, 10, '0901000008', 'Giao thuốc gấp',                             'DELIVERED',  4,    11,   NOW() - INTERVAL '6 days',   NOW() - INTERVAL '6 days'),
(5, 1, NULL, NULL, 12, '0901000006', 'Đổi ý, tự qua lấy',                     'CANCELED',   NULL, NULL, NOW() - INTERVAL '9 days',   NOW() - INTERVAL '9 days');

SELECT setval('locker_schema.drone_delivery_requests_id_seq', 5, true);


-- =====================================================================
-- 5) order_db — đơn hàng, lịch sử, đánh giá, khiếu nại, khuyến mãi, drone mission
-- =====================================================================
\connect order_db

TRUNCATE TABLE order_schema.drone_missions, order_schema.promotion_usages,
               order_schema.promotion_claims, order_schema.promotions,
               order_schema.order_complaints, order_schema.order_ratings,
               order_schema.order_status_history, order_schema.order_details,
               order_schema.orders
  RESTART IDENTITY CASCADE;

-- Khuyến mãi (tạo bởi admin id 1)
INSERT INTO order_schema.promotions
(id, code, name, discount_type, discount_value, max_discount_amount, min_order_amount, stackable, status, start_at, end_at, usage_count, created_by_user_id, image_url, description, locker_id, total_usage_limit, per_user_limit, created_at, updated_at)
VALUES
(1, 'WELCOME50K',  'Chào mừng khách mới - giảm 50.000đ', 'FIXED_AMOUNT', 50000, NULL,  100000, FALSE, 'ACTIVE',  NOW() - INTERVAL '180 days', NOW() + INTERVAL '180 days', 3, 1, 'https://picsum.photos/seed/welcome/600/300',  'Áp dụng cho đơn đầu tiên từ 100.000đ',            NULL, 1000, 1, NOW() - INTERVAL '180 days', NOW() - INTERVAL '2 days'),
(2, 'FREESHIP',    'Miễn phí giao hàng drone',           'FIXED_AMOUNT', 25000, NULL,  0,      TRUE,  'ACTIVE',  NOW() - INTERVAL '90 days',  NOW() + INTERVAL '90 days',  2, 1, 'https://picsum.photos/seed/freeship/600/300', 'Giảm phí giao cho đơn drone',                      NULL, 500,  3, NOW() - INTERVAL '90 days',  NOW() - INTERVAL '1 day'),
(3, 'WEEKEND20',   'Cuối tuần giảm 20%',                 'PERCENTAGE',   20,    40000, 50000,  FALSE, 'ACTIVE',  NOW() - INTERVAL '60 days',  NOW() + INTERVAL '30 days',  2, 1, 'https://picsum.photos/seed/weekend/600/300',  'Chỉ áp dụng thứ 7 và chủ nhật, giảm tối đa 40k',   NULL, 300,  2, NOW() - INTERVAL '60 days',  NOW() - INTERVAL '3 days'),
(4, 'PREMIUM30',   'Thành viên Gold giảm 30%',           'PERCENTAGE',   30,    60000, 100000, FALSE, 'ACTIVE',  NOW() - INTERVAL '45 days',  NOW() + INTERVAL '45 days',  1, 1, 'https://picsum.photos/seed/premium/600/300',  'Dành riêng cho hạng Gold trở lên',                 NULL, 200,  1, NOW() - INTERVAL '45 days',  NOW() - INTERVAL '5 days'),
(5, 'FLASHSALE',   'Flash sale 2 giờ vàng',              'PERCENTAGE',   25,    30000, 0,      FALSE, 'EXPIRED', NOW() - INTERVAL '30 days',  NOW() - INTERVAL '29 days',  0, 1, 'https://picsum.photos/seed/flash/600/300',    'Đã kết thúc',                                      NULL, 100,  1, NOW() - INTERVAL '30 days',  NOW() - INTERVAL '29 days'),
(6, 'DEMOFPT10',   'Ưu đãi riêng tủ FPT - giảm 10.000đ', 'FIXED_AMOUNT', 10000, NULL,  0,      TRUE,  'ACTIVE',  NOW() - INTERVAL '20 days',  NOW() + INTERVAL '70 days',  0, 1, 'https://picsum.photos/seed/fptdeal/600/300', 'Chỉ áp dụng tại tủ CAB-DEMO-01',                   1,    150,  2, NOW() - INTERVAL '20 days',  NULL),
(7, 'VIPDROPOFF',  'Khách VIP gửi đồ giảm 15%',          'PERCENTAGE',   15,    25000, 30000,  FALSE, 'ACTIVE',  NOW() - INTERVAL '15 days',  NOW() + INTERVAL '75 days',  0, 1, 'https://picsum.photos/seed/vipdrop/600/300', 'Áp dụng cho khách đã có từ 5 đơn thành công',     NULL, 100,  1, NOW() - INTERVAL '15 days',  NULL),
(8, 'BULKWASH',    'Gửi vali lớn giảm 35.000đ',          'FIXED_AMOUNT', 35000, NULL,  150000, FALSE, 'INACTIVE', NOW() - INTERVAL '10 days', NOW() + INTERVAL '80 days',  0, 1, 'https://picsum.photos/seed/bulk/600/300',    'Tạm ngưng để điều chỉnh ngân sách',               NULL, 50,   1, NOW() - INTERVAL '10 days',  NOW() - INTERVAL '2 days');

SELECT setval('order_schema.promotions_id_seq', 8, true);

-- Đơn hàng: đủ 4 loại (STORAGE / SEND / RENTAL / DRONE_DELIVERY) và đủ trạng thái.
-- Ô tủ được chiếm ở đây khớp đúng với locker_boxes ở trên.
INSERT INTO order_schema.orders
(id, order_code, user_id, receiver_id, receiver_phone, receiver_name, locker_id, send_box_id, receive_box_id, store_id, staff_id,
 type, service_category, status, extra_fee, discount, reservation_fee, storage_price, shipping_fee, total_price, original_price,
 promotion_code, applied_promotion_codes, pin_code, pin_code_issued_at, receive_at, intended_receive_at, completed_at, returned_at,
 pickup_deadline, description, customer_note, payment_status, paid_at, rental_duration_hours, last_reminder_at,
 receiver_user_id, destination_locker_id, reserved_box_id, parcel_weight_grams, delivery_stage, idempotency_key, fulfillment_mode,
 created_at, updated_at)
VALUES
-- Đơn đang hoạt động của KHÁCH CHÍNH (user 2)
(1,  'ORD-STG-0001', 2, NULL, NULL, NULL, 1, 4, NULL, 1, NULL, 'STORAGE', 'STORAGE', 'STORING',     0, 50000, 10000, 90000,  0, 50000,  100000, 'WELCOME50K', 'WELCOME50K', '481920', NOW() - INTERVAL '6 hours', NULL, NOW() + INTERVAL '18 hours', NULL, NULL, NOW() + INTERVAL '18 hours', 'Gửi balo và sách vở', 'Cho em gửi qua đêm nhé', 'PAID', NOW() - INTERVAL '6 hours', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '6 hours', NOW() - INTERVAL '6 hours'),
(2,  'ORD-RNT-0002', 2, NULL, NULL, NULL, 1, 7, NULL, 1, NULL, 'RENTAL',  'RENTAL',  'STORING',     0, 0,     20000, 120000, 0, 140000, 140000, NULL, NULL, '736104', NOW() - INTERVAL '20 hours', NULL, NOW() + INTERVAL '4 hours', NULL, NULL, NOW() + INTERVAL '4 hours', 'Thuê tủ 24h giữ vali', NULL, 'PAID', NOW() - INTERVAL '20 hours', 24, NOW() - INTERVAL '2 hours', NULL, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '20 hours', NOW() - INTERVAL '2 hours'),
(3,  'ORD-DRN-0003', 2, NULL, '0901000005', 'Trần Minh Anh', 1, NULL, NULL, 1, NULL, 'DRONE_DELIVERY', 'DELIVERY', 'AWAITING_DISPATCH', 0, 25000, 0, 0, 25000, 60000, 85000, 'FREESHIP', 'FREESHIP', NULL, NULL, NULL, NOW() + INTERVAL '3 hours', NULL, NULL, NULL, 'Giao hộp tài liệu bằng drone', 'Gọi trước khi thả nhé', 'PAID', NOW() - INTERVAL '3 hours', NULL, NULL, 5, 1, 1, 850, 'AWAITING_DISPATCH', 'idem-drn-0003', 'DRONE', NOW() - INTERVAL '3 hours', NOW() - INTERVAL '3 hours'),
-- Lịch sử đã hoàn tất của user 2
(4,  'ORD-STG-0004', 2, NULL, NULL, NULL, 1, 5, NULL, 1, NULL, 'STORAGE', 'STORAGE', 'COMPLETED',   0, 0,     10000, 60000,  0, 70000,  70000,  NULL, NULL, '203847', NOW() - INTERVAL '12 days', NOW() - INTERVAL '12 days', NOW() - INTERVAL '12 days', NOW() - INTERVAL '12 days', NULL, NOW() - INTERVAL '12 days', 'Gửi đồ tập gym', NULL, 'PAID', NOW() - INTERVAL '12 days', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '12 days', NOW() - INTERVAL '12 days'),
(5,  'ORD-SND-0005', 2, 5, '0901000005', 'Trần Minh Anh', 2, 13, 14, 2, NULL, 'SEND', 'SEND', 'COMPLETED', 0, 0, 10000, 45000, 0, 55000, 55000, NULL, NULL, '918273', NOW() - INTERVAL '20 days', NOW() - INTERVAL '19 days', NOW() - INTERVAL '19 days', NOW() - INTERVAL '19 days', NULL, NOW() - INTERVAL '19 days', 'Gửi sạc laptop cho bạn', NULL, 'PAID', NOW() - INTERVAL '20 days', NULL, NULL, 5, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '20 days', NOW() - INTERVAL '19 days'),
(6,  'ORD-RNT-0006', 2, NULL, NULL, NULL, 3, 23, NULL, 3, NULL, 'RENTAL', 'RENTAL', 'COMPLETED', 0, 24000, 20000, 100000, 0, 96000, 120000, 'WEEKEND20', 'WEEKEND20', '556677', NOW() - INTERVAL '35 days', NOW() - INTERVAL '34 days', NOW() - INTERVAL '34 days', NOW() - INTERVAL '34 days', NULL, NOW() - INTERVAL '34 days', 'Thuê tủ cuối tuần', NULL, 'PAID', NOW() - INTERVAL '35 days', 12, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '35 days', NOW() - INTERVAL '34 days'),
(7,  'ORD-STG-0007', 2, NULL, NULL, NULL, 1, 6, NULL, 1, NULL, 'STORAGE', 'STORAGE', 'CANCELED', 0, 0, 10000, 50000, 0, 60000, 60000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'Đổi lịch nên huỷ', 'Em huỷ do bận', 'PAID', NOW() - INTERVAL '45 days', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '45 days', NOW() - INTERVAL '45 days'),
-- Đơn của các khách khác
(8,  'ORD-STG-0008', 5,  NULL, NULL, NULL, 3, 21, NULL, 3, NULL, 'STORAGE', 'STORAGE', 'INITIALIZED', 0, 0, 10000, 55000, 0, 65000, 65000, NULL, NULL, NULL, NULL, NULL, NOW() + INTERVAL '10 hours', NULL, NULL, NOW() + INTERVAL '10 hours', 'Chờ mang đồ tới', NULL, 'UNPAID', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '15 minutes', NOW() - INTERVAL '15 minutes'),
(9,  'ORD-SND-0009', 6,  7, '0901000007', 'Nguyễn Văn Khang', 2, 15, 12, 2, NULL, 'SEND', 'SEND', 'READY_FOR_PICKUP', 0, 0, 10000, 40000, 0, 50000, 50000, NULL, NULL, '334455', NOW() - INTERVAL '10 hours', NULL, NOW() + INTERVAL '14 hours', NULL, NULL, NOW() + INTERVAL '14 hours', 'Gửi tai nghe', 'Nhờ shipper bỏ vào ô', 'PAID', NOW() - INTERVAL '10 hours', NULL, NULL, 7, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '10 hours', NOW() - INTERVAL '10 hours'),
(10, 'ORD-RNT-0010', 7,  NULL, NULL, NULL, 4, 30, NULL, 4, NULL, 'RENTAL', 'RENTAL', 'STORING', 0, 10000, 20000, 150000, 0, 160000, 170000, 'DEMOFPT10', 'DEMOFPT10', '667788', NOW() - INTERVAL '2 days', NULL, NOW() + INTERVAL '1 day', NULL, NULL, NOW() + INTERVAL '1 day', 'Thuê tủ 3 ngày', NULL, 'PAID', NOW() - INTERVAL '2 days', 72, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
(11, 'ORD-STG-0011', 8,  NULL, NULL, NULL, 1, 9, NULL, 1, NULL, 'STORAGE', 'STORAGE', 'COMPLETED', 0, 0, 10000, 35000, 0, 45000, 45000, NULL, NULL, '112233', NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days', NULL, NOW() - INTERVAL '8 days', 'Gửi túi xách', NULL, 'PAID', NOW() - INTERVAL '8 days', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days'),
(12, 'ORD-DRN-0012', 10, NULL, '0901000008', 'Phạm Thu Hà', 2, NULL, NULL, 2, NULL, 'DRONE_DELIVERY', 'DELIVERY', 'COMPLETED', 0, 25000, 0, 0, 25000, 55000, 80000, 'FREESHIP', 'FREESHIP', '445566', NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days', NULL, NOW() - INTERVAL '6 days', 'Giao thuốc gấp bằng drone', NULL, 'PAID', NOW() - INTERVAL '6 days', NULL, NULL, 8, 2, 12, 320, 'DELIVERED', 'idem-drn-0012', 'DRONE', NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days'),
(13, 'ORD-STG-0013', 9,  NULL, NULL, NULL, 3, 24, NULL, 3, NULL, 'STORAGE', 'STORAGE', 'EXPIRED', 0, 0, 10000, 40000, 0, 50000, 50000, NULL, NULL, NULL, NULL, NULL, NOW() - INTERVAL '38 days', NULL, NULL, NOW() - INTERVAL '38 days', 'Khách không tới lấy', NULL, 'UNPAID', NULL, NULL, NOW() - INTERVAL '39 days', NULL, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '40 days', NOW() - INTERVAL '38 days'),
(14, 'ORD-STG-0014', 12, NULL, NULL, NULL, 1, 10, NULL, 1, NULL, 'STORAGE', 'STORAGE', 'RETURNED', 0, 0, 20000, 130000, 0, 150000, 150000, NULL, NULL, '998877', NOW() - INTERVAL '25 days', NOW() - INTERVAL '24 days', NOW() - INTERVAL '24 days', NULL, NOW() - INTERVAL '24 days', NOW() - INTERVAL '24 days', 'Gửi vali size lớn', NULL, 'PAID', NOW() - INTERVAL '25 days', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '25 days', NOW() - INTERVAL '24 days'),
(15, 'ORD-SND-0015', 5,  2, '0901000002', 'Nguyễn Quốc Bảo', 1, 3, NULL, 1, NULL, 'SEND', 'SEND', 'COMPLETED', 0, 0, 10000, 42000, 0, 52000, 52000, NULL, NULL, '221100', NOW() - INTERVAL '28 days', NOW() - INTERVAL '27 days', NOW() - INTERVAL '27 days', NOW() - INTERVAL '27 days', NULL, NOW() - INTERVAL '27 days', 'Gửi tài liệu in', NULL, 'PAID', NOW() - INTERVAL '28 days', NULL, NULL, 2, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '28 days', NOW() - INTERVAL '27 days'),
(16, 'ORD-RNT-0016', 6,  NULL, NULL, NULL, 2, 16, NULL, 2, NULL, 'RENTAL', 'RENTAL', 'COMPLETED', 0, 36000, 20000, 160000, 0, 144000, 180000, 'PREMIUM30', 'PREMIUM30', '765432', NOW() - INTERVAL '18 days', NOW() - INTERVAL '17 days', NOW() - INTERVAL '17 days', NOW() - INTERVAL '17 days', NULL, NOW() - INTERVAL '17 days', 'Thuê tủ 2 ngày', NULL, 'PAID', NOW() - INTERVAL '18 days', 48, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '18 days', NOW() - INTERVAL '17 days'),
(17, 'ORD-STG-0017', 10, NULL, NULL, NULL, 2, 17, NULL, 2, NULL, 'STORAGE', 'STORAGE', 'CANCELED', 0, 0, 10000, 45000, 0, 55000, 55000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'Huỷ trước khi gửi', NULL, 'UNPAID', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days'),
(18, 'ORD-SND-0018', 8,  12, '0901000012', 'Ngô Bảo Châu', 3, 26, 27, 3, NULL, 'SEND', 'SEND', 'COMPLETED', 0, 0, 10000, 38000, 0, 48000, 48000, NULL, NULL, '135790', NOW() - INTERVAL '14 days', NOW() - INTERVAL '13 days', NOW() - INTERVAL '13 days', NOW() - INTERVAL '13 days', NULL, NOW() - INTERVAL '13 days', 'Gửi quà sinh nhật', NULL, 'PAID', NOW() - INTERVAL '14 days', NULL, NULL, 12, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '14 days', NOW() - INTERVAL '13 days'),
(19, 'ORD-STG-0019', 12, NULL, NULL, NULL, 2, 18, NULL, 2, NULL, 'STORAGE', 'STORAGE', 'COMPLETED', 0, 0, 10000, 30000, 0, 40000, 40000, NULL, NULL, '246810', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days', NULL, NOW() - INTERVAL '5 days', 'Gửi áo khoác', NULL, 'PAID', NOW() - INTERVAL '5 days', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),
(20, 'ORD-DRN-0020', 6,  NULL, '0901000010', 'Vũ Hoàng Nam', 1, NULL, NULL, 1, NULL, 'DRONE_DELIVERY', 'DELIVERY', 'COMPLETED', 0, 0, 0, 0, 30000, 75000, 75000, NULL, NULL, '369258', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days', NULL, NOW() - INTERVAL '3 days', 'Giao chìa khoá bằng drone', NULL, 'PAID', NOW() - INTERVAL '3 days', NULL, NULL, 10, 1, 2, 180, 'DELIVERED', 'idem-drn-0020', 'DRONE', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),
(21, 'ORD-DRN-0021', 5,  NULL, '0901000002', 'Nguyễn Quốc Bảo', 2, NULL, NULL, 2, NULL, 'DRONE_DELIVERY', 'DELIVERY', 'STORING', 0, 0, 0, 0, 30000, 80000, 80000, NULL, NULL, NULL, NULL, NULL, NOW() + INTERVAL '1 hour', NULL, NULL, NULL, 'Giao sạc dự phòng bằng drone', NULL, 'PAID', NOW() - INTERVAL '2 hours', NULL, NULL, 2, 2, 11, 420, 'IN_FLIGHT', 'idem-drn-0021', 'DRONE', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '30 minutes'),
(22, 'ORD-STG-0022', 7,  NULL, NULL, NULL, 1, 2, NULL, 1, NULL, 'STORAGE', 'STORAGE', 'COMPLETED', 0, 0, 10000, 32000, 0, 42000, 42000, NULL, NULL, '147258', NOW() - INTERVAL '22 days', NOW() - INTERVAL '22 days', NOW() - INTERVAL '22 days', NOW() - INTERVAL '22 days', NULL, NOW() - INTERVAL '22 days', 'Gửi mũ bảo hiểm', NULL, 'PAID', NOW() - INTERVAL '22 days', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '22 days', NOW() - INTERVAL '22 days'),
(23, 'ORD-RNT-0023', 12, NULL, NULL, NULL, 3, 22, NULL, 3, NULL, 'RENTAL', 'RENTAL', 'COMPLETED', 0, 0, 20000, 110000, 0, 130000, 130000, NULL, NULL, '852741', NOW() - INTERVAL '9 days', NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days', NULL, NOW() - INTERVAL '8 days', 'Thuê tủ 1 ngày', NULL, 'PAID', NOW() - INTERVAL '9 days', 24, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '9 days', NOW() - INTERVAL '8 days'),
(24, 'ORD-STG-0024', 11, NULL, NULL, NULL, 1, 9, NULL, 1, NULL, 'STORAGE', 'STORAGE', 'COMPLETED', 0, 0, 10000, 28000, 0, 38000, 38000, NULL, NULL, '963852', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', NULL, NOW() - INTERVAL '2 days', 'Gửi đồ nghề kỹ thuật', NULL, 'PAID', NOW() - INTERVAL '2 days', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'STANDARD', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days');

SELECT setval('order_schema.orders_id_seq', 24, true);

-- Chi tiết đơn (dịch vụ 1=Gửi đồ, 2=Thuê tủ, 3=Giao drone)
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price, description) VALUES
(1, 1, 1, 1, 90000,  'Gửi đồ tiêu chuẩn - qua đêm'),
(2, 2, 2, 1, 120000, 'Thuê tủ 24 giờ'),
(3, 3, 3, 1, 60000,  'Giao hàng bằng drone'),
(4, 4, 1, 1, 60000,  'Gửi đồ tiêu chuẩn'),
(5, 5, 1, 1, 45000,  'Gửi đồ cho người khác'),
(6, 6, 2, 1, 100000, 'Thuê tủ 12 giờ'),
(7, 7, 1, 1, 50000,  'Gửi đồ tiêu chuẩn'),
(8, 8, 1, 1, 55000,  'Gửi đồ tiêu chuẩn'),
(9, 9, 1, 1, 40000,  'Gửi đồ cho người khác'),
(10, 10, 2, 1, 150000, 'Thuê tủ 72 giờ'),
(11, 11, 1, 1, 35000, 'Gửi đồ tiêu chuẩn'),
(12, 12, 3, 1, 55000, 'Giao hàng bằng drone'),
(13, 13, 1, 1, 40000, 'Gửi đồ tiêu chuẩn'),
(14, 14, 1, 1, 130000, 'Gửi vali cỡ lớn (ô XL)'),
(15, 15, 1, 1, 42000, 'Gửi đồ cho người khác'),
(16, 16, 2, 1, 160000, 'Thuê tủ 48 giờ'),
(17, 17, 1, 1, 45000, 'Gửi đồ tiêu chuẩn'),
(18, 18, 1, 1, 38000, 'Gửi đồ cho người khác'),
(19, 19, 1, 1, 30000, 'Gửi đồ tiêu chuẩn'),
(20, 20, 3, 1, 75000, 'Giao hàng bằng drone'),
(21, 21, 3, 1, 80000, 'Giao hàng bằng drone'),
(22, 22, 1, 1, 32000, 'Gửi đồ tiêu chuẩn'),
(23, 23, 2, 1, 110000, 'Thuê tủ 24 giờ'),
(24, 24, 1, 1, 28000, 'Gửi đồ tiêu chuẩn');

SELECT setval('order_schema.order_details_id_seq', 24, true);

-- Lịch sử trạng thái: mỗi đơn ít nhất 2 mốc, khớp status hiện tại.
INSERT INTO order_schema.order_status_history (order_id, old_status, new_status, changed_by_user_id, note, created_at) VALUES
(1, NULL, 'INITIALIZED', 2, 'Khách tạo đơn trên app', NOW() - INTERVAL '6 hours 20 minutes'),
(1, 'INITIALIZED', 'STORING', 2, 'Đã bỏ đồ vào ô 4, tủ khoá lại', NOW() - INTERVAL '6 hours'),
(2, NULL, 'INITIALIZED', 2, 'Khách đặt thuê tủ 24h', NOW() - INTERVAL '20 hours 30 minutes'),
(2, 'INITIALIZED', 'STORING', 2, 'Đã mở ô 7 và cất vali', NOW() - INTERVAL '20 hours'),
(3, NULL, 'INITIALIZED', 2, 'Khách tạo yêu cầu giao drone', NOW() - INTERVAL '3 hours 10 minutes'),
(3, 'INITIALIZED', 'AWAITING_DISPATCH', 2, 'Thanh toán xong, chờ đội bay điều phối', NOW() - INTERVAL '3 hours'),
(4, NULL, 'INITIALIZED', 2, 'Tạo đơn', NOW() - INTERVAL '12 days 2 hours'),
(4, 'INITIALIZED', 'STORING', 2, 'Đã gửi đồ', NOW() - INTERVAL '12 days 1 hour'),
(4, 'STORING', 'COMPLETED', 2, 'Khách đã lấy đồ', NOW() - INTERVAL '12 days'),
(5, NULL, 'INITIALIZED', 2, 'Tạo đơn gửi cho người khác', NOW() - INTERVAL '20 days'),
(5, 'INITIALIZED', 'STORING', 2, 'Đã bỏ đồ vào ô', NOW() - INTERVAL '20 days'),
(5, 'STORING', 'COMPLETED', 5, 'Người nhận đã lấy', NOW() - INTERVAL '19 days'),
(6, NULL, 'INITIALIZED', 2, 'Đặt thuê tủ cuối tuần', NOW() - INTERVAL '35 days'),
(6, 'INITIALIZED', 'STORING', 2, 'Bắt đầu thuê', NOW() - INTERVAL '35 days'),
(6, 'STORING', 'COMPLETED', 2, 'Trả tủ đúng hạn', NOW() - INTERVAL '34 days'),
(7, NULL, 'INITIALIZED', 2, 'Tạo đơn', NOW() - INTERVAL '45 days'),
(7, 'INITIALIZED', 'CANCELED', 2, 'Khách tự huỷ, đã hoàn tiền về ví', NOW() - INTERVAL '45 days'),
(8, NULL, 'INITIALIZED', 5, 'Khách vừa đặt, đang giữ ô 21', NOW() - INTERVAL '15 minutes'),
(9, NULL, 'INITIALIZED', 6, 'Tạo đơn gửi tai nghe', NOW() - INTERVAL '10 hours 30 minutes'),
(9, 'INITIALIZED', 'STORING', 6, 'Đã bỏ đồ vào ô 15', NOW() - INTERVAL '10 hours'),
(9, 'STORING', 'READY_FOR_PICKUP', 6, 'Đã chuyển sang ô nhận 12, chờ người nhận', NOW() - INTERVAL '9 hours'),
(10, NULL, 'INITIALIZED', 7, 'Đặt thuê tủ 3 ngày', NOW() - INTERVAL '2 days 1 hour'),
(10, 'INITIALIZED', 'STORING', 7, 'Bắt đầu thuê ô 30', NOW() - INTERVAL '2 days'),
(11, NULL, 'INITIALIZED', 8, 'Tạo đơn', NOW() - INTERVAL '8 days 3 hours'),
(11, 'INITIALIZED', 'STORING', 8, 'Đã gửi túi xách', NOW() - INTERVAL '8 days 2 hours'),
(11, 'STORING', 'COMPLETED', 8, 'Đã lấy đồ', NOW() - INTERVAL '8 days'),
(12, NULL, 'INITIALIZED', 10, 'Tạo yêu cầu giao drone', NOW() - INTERVAL '6 days 3 hours'),
(12, 'INITIALIZED', 'AWAITING_DISPATCH', 10, 'Chờ điều phối', NOW() - INTERVAL '6 days 2 hours'),
(12, 'AWAITING_DISPATCH', 'COMPLETED', 11, 'Drone đã thả hàng vào ô 12, người nhận đã lấy', NOW() - INTERVAL '6 days'),
(13, NULL, 'INITIALIZED', 9, 'Tạo đơn', NOW() - INTERVAL '40 days'),
(13, 'INITIALIZED', 'EXPIRED', NULL, 'Quá hạn nhận, hệ thống tự huỷ và giải phóng ô', NOW() - INTERVAL '38 days'),
(14, NULL, 'INITIALIZED', 12, 'Tạo đơn gửi vali', NOW() - INTERVAL '25 days 1 hour'),
(14, 'INITIALIZED', 'STORING', 12, 'Đã cất vali vào ô XL', NOW() - INTERVAL '25 days'),
(14, 'STORING', 'RETURNED', 3, 'Nhân viên trả đồ tận tay do khách quên PIN', NOW() - INTERVAL '24 days'),
(15, NULL, 'INITIALIZED', 5, 'Tạo đơn', NOW() - INTERVAL '28 days'),
(15, 'INITIALIZED', 'STORING', 5, 'Đã gửi tài liệu', NOW() - INTERVAL '28 days'),
(15, 'STORING', 'COMPLETED', 2, 'Người nhận đã lấy', NOW() - INTERVAL '27 days'),
(16, NULL, 'INITIALIZED', 6, 'Đặt thuê tủ 2 ngày', NOW() - INTERVAL '18 days'),
(16, 'INITIALIZED', 'STORING', 6, 'Bắt đầu thuê', NOW() - INTERVAL '18 days'),
(16, 'STORING', 'COMPLETED', 6, 'Trả tủ', NOW() - INTERVAL '17 days'),
(17, NULL, 'INITIALIZED', 10, 'Tạo đơn', NOW() - INTERVAL '30 days'),
(17, 'INITIALIZED', 'CANCELED', 10, 'Huỷ trước khi thanh toán', NOW() - INTERVAL '30 days'),
(18, NULL, 'INITIALIZED', 8, 'Tạo đơn gửi quà', NOW() - INTERVAL '14 days'),
(18, 'INITIALIZED', 'STORING', 8, 'Đã bỏ quà vào ô 26', NOW() - INTERVAL '14 days'),
(18, 'STORING', 'COMPLETED', 12, 'Người nhận đã lấy ở ô 27', NOW() - INTERVAL '13 days'),
(19, NULL, 'INITIALIZED', 12, 'Tạo đơn', NOW() - INTERVAL '5 days 2 hours'),
(19, 'INITIALIZED', 'STORING', 12, 'Đã gửi áo khoác', NOW() - INTERVAL '5 days 1 hour'),
(19, 'STORING', 'COMPLETED', 12, 'Đã lấy đồ', NOW() - INTERVAL '5 days'),
(20, NULL, 'INITIALIZED', 6, 'Tạo yêu cầu giao drone', NOW() - INTERVAL '3 days 2 hours'),
(20, 'INITIALIZED', 'AWAITING_DISPATCH', 6, 'Chờ điều phối', NOW() - INTERVAL '3 days 1 hour'),
(20, 'AWAITING_DISPATCH', 'COMPLETED', 3, 'Drone đã giao xong', NOW() - INTERVAL '3 days'),
(21, NULL, 'INITIALIZED', 5, 'Tạo yêu cầu giao drone', NOW() - INTERVAL '2 hours 20 minutes'),
(21, 'INITIALIZED', 'AWAITING_DISPATCH', 5, 'Thanh toán xong', NOW() - INTERVAL '2 hours'),
(21, 'AWAITING_DISPATCH', 'STORING', 3, 'Đội bay đã cho DRONE-05 cất cánh', NOW() - INTERVAL '30 minutes'),
(22, NULL, 'INITIALIZED', 7, 'Tạo đơn', NOW() - INTERVAL '22 days'),
(22, 'INITIALIZED', 'STORING', 7, 'Đã gửi mũ bảo hiểm', NOW() - INTERVAL '22 days'),
(22, 'STORING', 'COMPLETED', 7, 'Đã lấy đồ', NOW() - INTERVAL '22 days'),
(23, NULL, 'INITIALIZED', 12, 'Đặt thuê tủ', NOW() - INTERVAL '9 days'),
(23, 'INITIALIZED', 'STORING', 12, 'Bắt đầu thuê', NOW() - INTERVAL '9 days'),
(23, 'STORING', 'COMPLETED', 12, 'Trả tủ đúng hạn', NOW() - INTERVAL '8 days'),
(24, NULL, 'INITIALIZED', 11, 'Tạo đơn', NOW() - INTERVAL '2 days 3 hours'),
(24, 'INITIALIZED', 'STORING', 11, 'Đã gửi đồ nghề', NOW() - INTERVAL '2 days 2 hours'),
(24, 'STORING', 'COMPLETED', 11, 'Đã lấy đồ', NOW() - INTERVAL '2 days');

-- Đánh giá: chỉ cho đơn đã COMPLETED
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, comment, created_at) VALUES
(1, 4,  2,  5, 'Tủ sạch, thao tác trên app rất nhanh.',              NOW() - INTERVAL '12 days'),
(2, 5,  2,  4, 'Bạn mình lấy được đồ dễ dàng, chỉ hơi khó tìm tủ.',  NOW() - INTERVAL '19 days'),
(3, 6,  2,  5, 'Thuê tủ cuối tuần rất tiện, giá hợp lý.',            NOW() - INTERVAL '34 days'),
(4, 11, 8,  4, 'Ổn, sẽ dùng lại.',                                   NOW() - INTERVAL '8 days'),
(5, 12, 10, 5, 'Drone giao nhanh không tưởng, 8 phút là tới.',       NOW() - INTERVAL '6 days'),
(6, 16, 6,  3, 'Tủ hơi chật so với vali của mình.',                  NOW() - INTERVAL '17 days'),
(7, 18, 8,  5, 'Gửi quà cho bạn rất tiện.',                          NOW() - INTERVAL '13 days'),
(8, 20, 6,  5, 'Lần đầu dùng drone, rất ấn tượng.',                  NOW() - INTERVAL '3 days'),
(9, 23, 12, 4, 'Tốt, mong có thêm tủ ở quận 7.',                     NOW() - INTERVAL '8 days');

SELECT setval('order_schema.order_ratings_id_seq', 9, true);

INSERT INTO order_schema.order_complaints (id, order_id, user_id, type, description, status, created_at) VALUES
(1, 14, 12, 'LOCKER_ISSUE',  'Em quên PIN, gọi hotline mãi mới có người ra mở giúp.',       'RESOLVED', NOW() - INTERVAL '24 days'),
(2, 13, 9,  'BILLING',       'Đơn quá hạn bị huỷ nhưng em vẫn muốn khiếu nại phí giữ ô.',   'CLOSED',   NOW() - INTERVAL '37 days'),
(3, 7,  2,  'REFUND',        'Huỷ đơn rồi mà chưa thấy tiền về ví.',                        'RESOLVED', NOW() - INTERVAL '45 days'),
(4, 16, 6,  'OTHER',         'Ô tủ nhỏ hơn mô tả trên app, mong cập nhật lại kích thước.',   'OPEN',     NOW() - INTERVAL '17 days'),
(5, 10, 7,  'LOCKER_ISSUE',  'Tủ Aeon đang bảo trì mà app vẫn cho đặt.',                    'OPEN',     NOW() - INTERVAL '2 days');

SELECT setval('order_schema.order_complaints_id_seq', 5, true);

-- Ví voucher của khách (promotion_claims) + lần dùng thật (promotion_usages)
INSERT INTO order_schema.promotion_claims (id, promotion_id, user_id, status, used_at, created_at) VALUES
(1,  1, 2,  'USED',  NOW() - INTERVAL '6 hours',  NOW() - INTERVAL '7 hours'),
(2,  1, 5,  'USED',  NOW() - INTERVAL '28 days',  NOW() - INTERVAL '29 days'),
(3,  1, 12, 'USED',  NOW() - INTERVAL '25 days',  NOW() - INTERVAL '26 days'),
(4,  2, 2,  'USED',  NOW() - INTERVAL '3 hours',  NOW() - INTERVAL '4 hours'),
(5,  2, 10, 'USED',  NOW() - INTERVAL '6 days',   NOW() - INTERVAL '7 days'),
(6,  3, 2,  'USED',  NOW() - INTERVAL '35 days',  NOW() - INTERVAL '36 days'),
(7,  3, 8,  'SAVED', NULL,                        NOW() - INTERVAL '4 days'),
(8,  4, 6,  'USED',  NOW() - INTERVAL '18 days',  NOW() - INTERVAL '19 days'),
(9,  6, 7,  'SAVED', NULL,                        NOW() - INTERVAL '3 days'),
(10, 7, 2,  'SAVED', NULL,                        NOW() - INTERVAL '2 days'),
(11, 3, 12, 'SAVED', NULL,                        NOW() - INTERVAL '1 day'),
(12, 6, 2,  'SAVED', NULL,                        NOW() - INTERVAL '5 days');

SELECT setval('order_schema.promotion_claims_id_seq', 12, true);

INSERT INTO order_schema.promotion_usages (id, promotion_id, user_id, order_id, discount_applied, created_at) VALUES
(1, 1, 2,  1,  50000, NOW() - INTERVAL '6 hours'),
(2, 2, 2,  3,  25000, NOW() - INTERVAL '3 hours'),
(3, 3, 2,  6,  24000, NOW() - INTERVAL '35 days'),
(4, 2, 10, 12, 25000, NOW() - INTERVAL '6 days'),
(5, 4, 6,  16, 36000, NOW() - INTERVAL '18 days'),
(6, 1, 5,  15, 0,     NOW() - INTERVAL '28 days'),
(7, 1, 12, 14, 0,     NOW() - INTERVAL '25 days'),
(8, 3, 12, 23, 0,     NOW() - INTERVAL '9 days');

SELECT setval('order_schema.promotion_usages_id_seq', 8, true);

-- Nhiệm vụ bay: một mission cho mỗi đơn DRONE_DELIVERY
INSERT INTO order_schema.drone_missions
(id, order_id, drone_unit_id, source_locker_id, destination_locker_id, status, assigned_by_user_id,
 last_accept_idempotency_key, last_launch_idempotency_key, ready_to_launch_at, launching_at, created_at, updated_at)
VALUES
(1, 3,  NULL, 1, 1, 'CREATED',        NULL, NULL,               NULL,               NULL,                        NULL,                        NOW() - INTERVAL '3 hours', NOW() - INTERVAL '3 hours'),
(2, 12, 4,    2, 2, 'ARRIVED',        11,   'acc-idem-0012',    'lch-idem-0012',    NOW() - INTERVAL '6 days',   NOW() - INTERVAL '6 days',   NOW() - INTERVAL '6 days',  NOW() - INTERVAL '6 days'),
(3, 20, 1,    1, 1, 'ARRIVED',        3,    'acc-idem-0020',    'lch-idem-0020',    NOW() - INTERVAL '3 days',   NOW() - INTERVAL '3 days',   NOW() - INTERVAL '3 days',  NOW() - INTERVAL '3 days'),
(4, 21, 5,    2, 2, 'LAUNCHING',      3,    'acc-idem-0021',    'lch-idem-0021',    NOW() - INTERVAL '45 minutes', NOW() - INTERVAL '30 minutes', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '30 minutes');

SELECT setval('order_schema.drone_missions_id_seq', 4, true);


-- =====================================================================
-- 6) payment_db — thanh toán, hoàn tiền, ví
-- =====================================================================
\connect payment_db

TRUNCATE TABLE payment_schema.wallet_transactions, payment_schema.wallets,
               payment_schema.refunds, payment_schema.payments
  RESTART IDENTITY CASCADE;

-- Mỗi đơn PAID có đúng một payment COMPLETED, amount = total_price của đơn.
-- order_id = 0 là sentinel cho giao dịch nạp ví.
INSERT INTO payment_schema.payments
(id, order_id, user_id, amount, method, status, reference_id, reference_transaction_id, content, qr, url, deeplink, description, created_at, updated_at)
VALUES
(1,  1,  2,  50000,  'WALLET', 'COMPLETED', 'PAY-ORD-0001', 'WLT-0001', 'Thanh toán đơn ORD-STG-0001', NULL, NULL, NULL, 'Trừ ví nội bộ',            NOW() - INTERVAL '6 hours',  NOW() - INTERVAL '6 hours'),
(2,  2,  2,  140000, 'VNPAY',  'COMPLETED', 'PAY-ORD-0002', 'VNP-88120041', 'Thanh toán đơn ORD-RNT-0002', 'vnpay-qr-0002', 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?ref=PAY-ORD-0002', NULL, 'VNPay sandbox', NOW() - INTERVAL '20 hours', NOW() - INTERVAL '20 hours'),
(3,  3,  2,  60000,  'MOMO',   'COMPLETED', 'PAY-ORD-0003', 'MOMO-77310028', 'Thanh toán đơn ORD-DRN-0003', 'momo-qr-0003', 'https://test-payment.momo.vn/pay/PAY-ORD-0003', 'momo://app?action=pay&ref=PAY-ORD-0003', 'MoMo sandbox', NOW() - INTERVAL '3 hours', NOW() - INTERVAL '3 hours'),
(4,  4,  2,  70000,  'WALLET', 'COMPLETED', 'PAY-ORD-0004', 'WLT-0004', 'Thanh toán đơn ORD-STG-0004', NULL, NULL, NULL, 'Trừ ví nội bộ', NOW() - INTERVAL '12 days', NOW() - INTERVAL '12 days'),
(5,  5,  2,  55000,  'CASH',   'COMPLETED', 'PAY-ORD-0005', NULL, 'Thanh toán đơn ORD-SND-0005', NULL, NULL, NULL, 'Tiền mặt tại kiosk', NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days'),
(6,  6,  2,  96000,  'VNPAY',  'COMPLETED', 'PAY-ORD-0006', 'VNP-88110033', 'Thanh toán đơn ORD-RNT-0006', NULL, NULL, NULL, 'VNPay sandbox', NOW() - INTERVAL '35 days', NOW() - INTERVAL '35 days'),
(7,  7,  2,  60000,  'WALLET', 'COMPLETED', 'PAY-ORD-0007', 'WLT-0007', 'Thanh toán đơn ORD-STG-0007 (đã hoàn)', NULL, NULL, NULL, 'Đơn bị huỷ, đã hoàn tiền', NOW() - INTERVAL '45 days', NOW() - INTERVAL '45 days'),
(8,  9,  6,  50000,  'MOMO',   'COMPLETED', 'PAY-ORD-0009', 'MOMO-77310099', 'Thanh toán đơn ORD-SND-0009', NULL, NULL, NULL, 'MoMo sandbox', NOW() - INTERVAL '10 hours', NOW() - INTERVAL '10 hours'),
(9,  10, 7,  160000, 'VNPAY',  'COMPLETED', 'PAY-ORD-0010', 'VNP-88120100', 'Thanh toán đơn ORD-RNT-0010', NULL, NULL, NULL, 'VNPay sandbox', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
(10, 11, 8,  45000,  'CASH',   'COMPLETED', 'PAY-ORD-0011', NULL, 'Thanh toán đơn ORD-STG-0011', NULL, NULL, NULL, 'Tiền mặt tại kiosk', NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days'),
(11, 12, 10, 55000,  'WALLET', 'COMPLETED', 'PAY-ORD-0012', 'WLT-0012', 'Thanh toán đơn ORD-DRN-0012', NULL, NULL, NULL, 'Trừ ví nội bộ', NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days'),
(12, 14, 12, 150000, 'VNPAY',  'COMPLETED', 'PAY-ORD-0014', 'VNP-88090077', 'Thanh toán đơn ORD-STG-0014', NULL, NULL, NULL, 'VNPay sandbox', NOW() - INTERVAL '25 days', NOW() - INTERVAL '25 days'),
(13, 15, 5,  52000,  'WALLET', 'COMPLETED', 'PAY-ORD-0015', 'WLT-0015', 'Thanh toán đơn ORD-SND-0015', NULL, NULL, NULL, 'Trừ ví nội bộ', NOW() - INTERVAL '28 days', NOW() - INTERVAL '28 days'),
(14, 16, 6,  144000, 'MOMO',   'COMPLETED', 'PAY-ORD-0016', 'MOMO-77300055', 'Thanh toán đơn ORD-RNT-0016', NULL, NULL, NULL, 'MoMo sandbox', NOW() - INTERVAL '18 days', NOW() - INTERVAL '18 days'),
(15, 18, 8,  48000,  'CASH',   'COMPLETED', 'PAY-ORD-0018', NULL, 'Thanh toán đơn ORD-SND-0018', NULL, NULL, NULL, 'Tiền mặt tại kiosk', NOW() - INTERVAL '14 days', NOW() - INTERVAL '14 days'),
(16, 19, 12, 40000,  'WALLET', 'COMPLETED', 'PAY-ORD-0019', 'WLT-0019', 'Thanh toán đơn ORD-STG-0019', NULL, NULL, NULL, 'Trừ ví nội bộ', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),
(17, 20, 6,  75000,  'VNPAY',  'COMPLETED', 'PAY-ORD-0020', 'VNP-88130012', 'Thanh toán đơn ORD-DRN-0020', NULL, NULL, NULL, 'VNPay sandbox', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),
(18, 21, 5,  80000,  'WALLET', 'COMPLETED', 'PAY-ORD-0021', 'WLT-0021', 'Thanh toán đơn ORD-DRN-0021', NULL, NULL, NULL, 'Trừ ví nội bộ', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours'),
(19, 22, 7,  42000,  'CASH',   'COMPLETED', 'PAY-ORD-0022', NULL, 'Thanh toán đơn ORD-STG-0022', NULL, NULL, NULL, 'Tiền mặt tại kiosk', NOW() - INTERVAL '22 days', NOW() - INTERVAL '22 days'),
(20, 23, 12, 130000, 'VNPAY',  'COMPLETED', 'PAY-ORD-0023', 'VNP-88100044', 'Thanh toán đơn ORD-RNT-0023', NULL, NULL, NULL, 'VNPay sandbox', NOW() - INTERVAL '9 days', NOW() - INTERVAL '9 days'),
(21, 24, 11, 38000,  'WALLET', 'COMPLETED', 'PAY-ORD-0024', 'WLT-0024', 'Thanh toán đơn ORD-STG-0024', NULL, NULL, NULL, 'Trừ ví nội bộ', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
-- Đơn chưa thanh toán / thất bại
(22, 8,  5,  65000,  'VNPAY',  'PENDING',   'PAY-ORD-0008', NULL, 'Chờ thanh toán đơn ORD-STG-0008', NULL, 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?ref=PAY-ORD-0008', NULL, 'Khách chưa hoàn tất', NOW() - INTERVAL '15 minutes', NULL),
(23, 17, 10, 55000,  'MOMO',   'FAILED',    'PAY-ORD-0017', 'MOMO-77290011', 'Thanh toán thất bại ORD-STG-0017', NULL, NULL, NULL, 'Khách huỷ giữa chừng', NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days'),
-- Nạp ví (order_id = 0)
(24, 0, 2,  500000, 'VNPAY', 'COMPLETED', 'TOPUP-0002-A', 'VNP-90010001', 'Nạp ví 500.000đ',  NULL, NULL, NULL, 'Nạp ví qua VNPay', NOW() - INTERVAL '60 days', NOW() - INTERVAL '60 days'),
(25, 0, 2,  300000, 'MOMO',  'COMPLETED', 'TOPUP-0002-B', 'MOMO-90010002', 'Nạp ví 300.000đ', NULL, NULL, NULL, 'Nạp ví qua MoMo',  NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'),
(26, 0, 5,  400000, 'VNPAY', 'COMPLETED', 'TOPUP-0005-A', 'VNP-90010003', 'Nạp ví 400.000đ',  NULL, NULL, NULL, 'Nạp ví qua VNPay', NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days'),
(27, 0, 10, 200000, 'MOMO',  'COMPLETED', 'TOPUP-0010-A', 'MOMO-90010004', 'Nạp ví 200.000đ', NULL, NULL, NULL, 'Nạp ví qua MoMo',  NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days'),
(28, 0, 12, 250000, 'VNPAY', 'COMPLETED', 'TOPUP-0012-A', 'VNP-90010005', 'Nạp ví 250.000đ',  NULL, NULL, NULL, 'Nạp ví qua VNPay', NOW() - INTERVAL '12 days', NOW() - INTERVAL '12 days'),
(29, 0, 11, 100000, 'VNPAY', 'COMPLETED', 'TOPUP-0011-A', 'VNP-90010006', 'Nạp ví 100.000đ',  NULL, NULL, NULL, 'Nạp ví qua VNPay', NOW() - INTERVAL '15 days', NOW() - INTERVAL '15 days');

SELECT setval('payment_schema.payments_id_seq', 29, true);

-- Hoàn tiền: cho đơn đã thanh toán rồi bị huỷ
INSERT INTO payment_schema.refunds
(id, payment_id, order_id, amount, status, reason, transaction_id, processed_by_user_id, requested_at, processed_at)
VALUES
(1, 7,  7,  60000, 'COMPLETED', 'Khách huỷ đơn trước khi gửi đồ',                'RFD-0007', 1, NOW() - INTERVAL '45 days', NOW() - INTERVAL '44 days'),
(2, 12, 14, 20000, 'COMPLETED', 'Hoàn một phần do khách phải chờ nhân viên mở ô', 'RFD-0014', 1, NOW() - INTERVAL '24 days', NOW() - INTERVAL '23 days'),
(3, 9,  10, 30000, 'PENDING',   'Khách khiếu nại tủ Aeon đang bảo trì',          NULL,       NULL, NOW() - INTERVAL '2 days', NULL);

SELECT setval('payment_schema.refunds_id_seq', 3, true);

-- Ví: một ví cho mỗi user. balance khớp đúng tổng sổ cái bên dưới.
INSERT INTO payment_schema.wallets (id, user_id, balance, currency, version, created_at, updated_at) VALUES
(1,  1,  0,      'VND', 0, NOW() - INTERVAL '400 days', NULL),
(2,  2,  550000, 'VND', 6, NOW() - INTERVAL '320 days', NOW() - INTERVAL '6 hours'),
(3,  3,  0,      'VND', 0, NOW() - INTERVAL '300 days', NULL),
(4,  4,  0,      'VND', 0, NOW() - INTERVAL '300 days', NULL),
(5,  5,  268000, 'VND', 3, NOW() - INTERVAL '210 days', NOW() - INTERVAL '2 hours'),
(6,  6,  0,      'VND', 0, NOW() - INTERVAL '180 days', NULL),
(7,  7,  0,      'VND', 0, NOW() - INTERVAL '150 days', NULL),
(8,  8,  0,      'VND', 0, NOW() - INTERVAL '120 days', NULL),
(9,  9,  0,      'VND', 0, NOW() - INTERVAL '110 days', NULL),
(10, 10, 145000, 'VND', 2, NOW() - INTERVAL '90 days',  NOW() - INTERVAL '6 days'),
(11, 11, 62000,  'VND', 2, NOW() - INTERVAL '260 days', NOW() - INTERVAL '2 days'),
(12, 12, 210000, 'VND', 2, NOW() - INTERVAL '60 days',  NOW() - INTERVAL '5 days');

SELECT setval('payment_schema.wallets_id_seq', 12, true);

-- Sổ cái ví: balance_after cộng dồn đúng, kết thúc đúng bằng wallets.balance.
-- user 2:  +500000 → 500000; -70000 → 430000; +60000(hoàn) → 490000; +300000 → 790000;
--          -190000(gộp đơn 15? không) ... chi tiết từng dòng bên dưới.
INSERT INTO payment_schema.wallet_transactions
(id, wallet_id, user_id, type, amount, balance_after, source, reference_id, description, created_at)
VALUES
-- user 2 (ví chính, kết thúc 550.000) — id tăng dần đúng theo thời gian
(1,  2, 2, 'CREDIT', 500000, 500000, 'TOPUP',         'TOPUP-0002-A', 'Nạp ví qua VNPay',                    NOW() - INTERVAL '60 days'),
(2,  2, 2, 'DEBIT',  60000,  440000, 'ORDER_PAYMENT', 'PAY-ORD-0007', 'Thanh toán đơn ORD-STG-0007',         NOW() - INTERVAL '45 days'),
(3,  2, 2, 'CREDIT', 60000,  500000, 'REFUND',        'RFD-0007',     'Hoàn tiền đơn ORD-STG-0007 đã huỷ',   NOW() - INTERVAL '44 days'),
(4,  2, 2, 'DEBIT',  70000,  430000, 'ORDER_PAYMENT', 'PAY-ORD-0004', 'Thanh toán đơn ORD-STG-0004',         NOW() - INTERVAL '12 days'),
(5,  2, 2, 'CREDIT', 300000, 730000, 'TOPUP',         'TOPUP-0002-B', 'Nạp ví qua MoMo',                     NOW() - INTERVAL '10 days'),
(6,  2, 2, 'DEBIT',  130000, 600000, 'ADJUST',        'ADJ-0002-A',   'Admin điều chỉnh: trừ phí quá hạn',   NOW() - INTERVAL '9 days'),
(7,  2, 2, 'DEBIT',  50000,  550000, 'ORDER_PAYMENT', 'PAY-ORD-0001', 'Thanh toán đơn ORD-STG-0001',         NOW() - INTERVAL '6 hours'),
-- user 5 (kết thúc 268.000)
(8,  5, 5, 'CREDIT', 400000, 400000, 'TOPUP',         'TOPUP-0005-A', 'Nạp ví qua VNPay',                    NOW() - INTERVAL '30 days'),
(9,  5, 5, 'DEBIT',  52000,  348000, 'ORDER_PAYMENT', 'PAY-ORD-0015', 'Thanh toán đơn ORD-SND-0015',         NOW() - INTERVAL '28 days'),
(10, 5, 5, 'DEBIT',  80000,  268000, 'ORDER_PAYMENT', 'PAY-ORD-0021', 'Thanh toán đơn ORD-DRN-0021',         NOW() - INTERVAL '2 hours'),
-- user 10 (kết thúc 145.000)
(11, 10, 10, 'CREDIT', 200000, 200000, 'TOPUP',         'TOPUP-0010-A', 'Nạp ví qua MoMo',                   NOW() - INTERVAL '20 days'),
(12, 10, 10, 'DEBIT',  55000,  145000, 'ORDER_PAYMENT', 'PAY-ORD-0012', 'Thanh toán đơn ORD-DRN-0012',       NOW() - INTERVAL '6 days'),
-- user 11 (kết thúc 62.000)
(13, 11, 11, 'CREDIT', 100000, 100000, 'TOPUP',         'TOPUP-0011-A', 'Nạp ví qua VNPay',                  NOW() - INTERVAL '15 days'),
(14, 11, 11, 'DEBIT',  38000,  62000,  'ORDER_PAYMENT', 'PAY-ORD-0024', 'Thanh toán đơn ORD-STG-0024',       NOW() - INTERVAL '2 days'),
-- user 12 (kết thúc 210.000)
(15, 12, 12, 'CREDIT', 250000, 250000, 'TOPUP',         'TOPUP-0012-A', 'Nạp ví qua VNPay',                  NOW() - INTERVAL '12 days'),
(16, 12, 12, 'DEBIT',  40000,  210000, 'ORDER_PAYMENT', 'PAY-ORD-0019', 'Thanh toán đơn ORD-STG-0019',       NOW() - INTERVAL '5 days');

SELECT setval('payment_schema.wallet_transactions_id_seq', 16, true);


-- =====================================================================
-- 7) notification_db — thông báo + FCM token
-- =====================================================================
\connect notification_db

TRUNCATE TABLE notification_schema.fcm_tokens, notification_schema.notifications
  RESTART IDENTITY CASCADE;

INSERT INTO notification_schema.notifications
(id, user_id, title, message, type, reference_id, reference_type, status, is_read, read_at, created_at)
VALUES
(1,  2, 'Đơn ORD-STG-0001 đã được gửi',      'Đồ của bạn đã nằm trong ô số 4 tại tủ CAB-DEMO-01. PIN: 481920.',        'ORDER',    1,  'ORDER',   'UNREAD', FALSE, NULL,                        NOW() - INTERVAL '6 hours'),
(2,  2, 'Thanh toán thành công',              'Đã trừ 50.000đ từ ví cho đơn ORD-STG-0001.',                            'PAYMENT',  1,  'ORDER',   'UNREAD', FALSE, NULL,                        NOW() - INTERVAL '6 hours'),
(3,  2, 'Sắp hết hạn thuê tủ',                'Đơn ORD-RNT-0002 sẽ hết hạn sau 4 giờ nữa. Vui lòng lấy đồ đúng hạn.',  'ORDER',    2,  'ORDER',   'UNREAD', FALSE, NULL,                        NOW() - INTERVAL '2 hours'),
(4,  2, 'Yêu cầu giao drone đã nhận',         'Đơn ORD-DRN-0003 đang chờ đội bay điều phối.',                          'DELIVERY', 3,  'ORDER',   'READ',   TRUE,  NOW() - INTERVAL '2 hours',  NOW() - INTERVAL '3 hours'),
(5,  2, 'Voucher mới cho bạn',                'Bạn vừa nhận voucher VIPDROPOFF giảm 15% cho đơn gửi đồ.',              'PROMOTION',7,  'PROMOTION','READ',  TRUE,  NOW() - INTERVAL '1 day',    NOW() - INTERVAL '2 days'),
(6,  2, 'Đơn ORD-STG-0004 hoàn tất',          'Cảm ơn bạn đã sử dụng dịch vụ. Đánh giá giúp chúng tôi nhé!',           'ORDER',    4,  'ORDER',   'READ',   TRUE,  NOW() - INTERVAL '12 days',  NOW() - INTERVAL '12 days'),
(7,  2, 'Hoàn tiền thành công',               'Đã hoàn 60.000đ vào ví cho đơn ORD-STG-0007.',                          'PAYMENT',  7,  'ORDER',   'READ',   TRUE,  NOW() - INTERVAL '44 days',  NOW() - INTERVAL '44 days'),
(8,  3, 'Phiếu sự cố mới được giao',          'Bạn được giao phiếu #1: Ô số 8 không đóng được cửa (tủ CAB-DEMO-01).',  'MAINTENANCE', 1, 'REPORT', 'UNREAD', FALSE, NULL,                       NOW() - INTERVAL '2 days'),
(9,  3, 'DRONE-03 chuyển trạng thái FAULT',   'Pin 15%, lỗi cảm biến GPS. Cần kiểm tra.',                              'MAINTENANCE', 3, 'DRONE',  'UNREAD', FALSE, NULL,                       NOW() - INTERVAL '2 days'),
(10, 3, 'Yêu cầu giao drone đang chờ',        'Có 1 yêu cầu giao drone đang chờ điều phối tại tủ CAB-DEMO-01.',        'DELIVERY', 1,  'DRONE_REQUEST','UNREAD', FALSE, NULL,                   NOW() - INTERVAL '3 hours'),
(11, 3, 'Lịch bảo trì tới hạn',               'Kiểm tra bãi đáp drone + marker tại CAB-DEMO-01 đến hạn sau 1 ngày.',   'MAINTENANCE', 2, 'SCHEDULE','READ',  TRUE,  NOW() - INTERVAL '1 day',    NOW() - INTERVAL '2 days'),
(12, 3, 'Phiếu #4 đã được đánh giá 4 sao',    'Khách hàng đánh giá: "Ô sạch hơn hẳn, cảm ơn đội bảo trì."',            'MAINTENANCE', 4, 'REPORT', 'READ',   TRUE,  NOW() - INTERVAL '11 days',  NOW() - INTERVAL '11 days'),
(13, 4, 'Phiếu sự cố mới được giao',          'Bạn được giao phiếu #6: DRONE-06 rơi khi hạ cánh (tủ CAB-AEON-01).',    'MAINTENANCE', 6, 'REPORT', 'UNREAD', FALSE, NULL,                       NOW() - INTERVAL '18 days'),
(14, 4, 'Tủ CAB-AEON-01 chuyển bảo trì',      'Toàn bộ tủ Aeon Tân Phú đã chuyển trạng thái MAINTENANCE.',             'SYSTEM',   4,  'LOCKER',  'READ',   TRUE,  NOW() - INTERVAL '5 days',   NOW() - INTERVAL '5 days'),
(15, 4, 'Phiếu #3 đã đóng',                   'Phiếu "Toàn bộ tủ không phản hồi" đã được bạn xử lý xong.',             'MAINTENANCE', 3, 'REPORT', 'READ',   TRUE,  NOW() - INTERVAL '4 days',   NOW() - INTERVAL '4 days'),
(16, 4, 'Lịch bảo trì quá hạn',               'Kiểm tra pin DRONE-04 đã quá hạn 3 ngày.',                              'MAINTENANCE', 6, 'SCHEDULE','UNREAD', FALSE, NULL,                       NOW() - INTERVAL '3 days'),
(17, 1, 'Báo cáo doanh thu tuần',             'Tuần này có 8 đơn hoàn tất, doanh thu 611.000đ.',                       'SYSTEM',   NULL, NULL,    'UNREAD', FALSE, NULL,                        NOW() - INTERVAL '1 day'),
(18, 1, 'Có khiếu nại mới cần xử lý',         '2 khiếu nại đang ở trạng thái OPEN.',                                   'SYSTEM',   NULL, NULL,    'UNREAD', FALSE, NULL,                        NOW() - INTERVAL '2 days'),
(19, 1, 'Ô tủ báo lỗi',                       'Có 3 ô đang ở trạng thái FAULT trên toàn hệ thống.',                    'SYSTEM',   NULL, NULL,    'READ',   TRUE,  NOW() - INTERVAL '2 days',   NOW() - INTERVAL '3 days'),
(20, 5, 'Đơn ORD-STG-0008 chờ thanh toán',    'Ô 21 đang được giữ cho bạn trong 45 phút.',                             'ORDER',    8,  'ORDER',   'UNREAD', FALSE, NULL,                        NOW() - INTERVAL '15 minutes'),
(21, 5, 'Drone đang trên đường',              'Đơn ORD-DRN-0021 đã cất cánh, dự kiến tới sau 10 phút.',                'DELIVERY', 21, 'ORDER',   'UNREAD', FALSE, NULL,                        NOW() - INTERVAL '30 minutes'),
(22, 6, 'Đơn ORD-SND-0009 sẵn sàng để lấy',   'Người nhận có thể lấy hàng ở ô số 2, tủ CAB-VGP-01.',                   'ORDER',    9,  'ORDER',   'READ',   TRUE,  NOW() - INTERVAL '8 hours',  NOW() - INTERVAL '9 hours'),
(23, 6, 'Đơn drone đã giao xong',             'Đơn ORD-DRN-0020 đã giao thành công.',                                  'DELIVERY', 20, 'ORDER',   'READ',   TRUE,  NOW() - INTERVAL '3 days',   NOW() - INTERVAL '3 days'),
(24, 7, 'Sắp hết hạn thuê tủ',                'Đơn ORD-RNT-0010 còn 1 ngày. Gia hạn nếu cần nhé.',                     'ORDER',    10, 'ORDER',   'UNREAD', FALSE, NULL,                        NOW() - INTERVAL '12 hours'),
(25, 7, 'Khiếu nại của bạn đang được xử lý',  'Chúng tôi đã tiếp nhận khiếu nại về tủ Aeon.',                          'SYSTEM',   5,  'COMPLAINT','UNREAD',FALSE, NULL,                        NOW() - INTERVAL '2 days'),
(26, 8, 'Đơn ORD-SND-0018 hoàn tất',          'Người nhận đã lấy quà thành công.',                                     'ORDER',    18, 'ORDER',   'READ',   TRUE,  NOW() - INTERVAL '13 days',  NOW() - INTERVAL '13 days'),
(27, 8, 'Voucher WEEKEND20 sắp hết hạn',      'Voucher giảm 20% của bạn còn hiệu lực 30 ngày.',                        'PROMOTION',3,  'PROMOTION','UNREAD',FALSE, NULL,                        NOW() - INTERVAL '4 days'),
(28, 9, 'Đơn ORD-STG-0013 đã quá hạn',        'Đơn bị huỷ tự động do quá hạn nhận. Vui lòng liên hệ hỗ trợ.',          'ORDER',    13, 'ORDER',   'READ',   TRUE,  NOW() - INTERVAL '38 days',  NOW() - INTERVAL '38 days'),
(29, 10, 'Đơn drone đã giao xong',            'Đơn ORD-DRN-0012 đã giao thành công vào ô số 2.',                       'DELIVERY', 12, 'ORDER',   'READ',   TRUE,  NOW() - INTERVAL '6 days',   NOW() - INTERVAL '6 days'),
(30, 10, 'Thanh toán thất bại',               'Giao dịch MoMo cho đơn ORD-STG-0017 không thành công.',                 'PAYMENT',  17, 'ORDER',   'READ',   TRUE,  NOW() - INTERVAL '30 days',  NOW() - INTERVAL '30 days'),
(31, 11, 'Phiếu #2 được giao cho bạn',        'Ô số 6 kẹt khoá tại tủ CAB-LM81-01.',                                   'MAINTENANCE', 2, 'REPORT', 'UNREAD', FALSE, NULL,                       NOW() - INTERVAL '8 days'),
(32, 11, 'Đơn ORD-STG-0024 hoàn tất',         'Cảm ơn bạn đã sử dụng dịch vụ.',                                        'ORDER',    24, 'ORDER',   'READ',   TRUE,  NOW() - INTERVAL '2 days',   NOW() - INTERVAL '2 days'),
(33, 12, 'Đơn ORD-STG-0019 hoàn tất',         'Bạn đã lấy đồ thành công.',                                             'ORDER',    19, 'ORDER',   'READ',   TRUE,  NOW() - INTERVAL '5 days',   NOW() - INTERVAL '5 days'),
(34, 12, 'Nạp ví thành công',                 'Đã cộng 250.000đ vào ví của bạn.',                                      'PAYMENT',  NULL, NULL,    'READ',   TRUE,  NOW() - INTERVAL '12 days',  NOW() - INTERVAL '12 days');

SELECT setval('notification_schema.notifications_id_seq', 34, true);

INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at, updated_at) VALUES
(1, 2,  'fcm-token-customer-nqbhuy-a91f3c7d2e', 'ANDROID', NOW() - INTERVAL '60 days', NOW() - INTERVAL '5 hours'),
(2, 3,  'fcm-token-maintenance-se180211-b72d',  'ANDROID', NOW() - INTERVAL '50 days', NOW() - INTERVAL '1 day'),
(3, 4,  'fcm-token-manager-huynqb-c83e',        'ANDROID', NOW() - INTERVAL '50 days', NOW() - INTERVAL '8 hours'),
(4, 5,  'fcm-token-minhanh-d94f',               'ANDROID', NOW() - INTERVAL '40 days', NOW() - INTERVAL '3 days'),
(5, 6,  'fcm-token-yenvi-e05a',                 'IOS',     NOW() - INTERVAL '35 days', NOW() - INTERVAL '4 days'),
(6, 10, 'fcm-token-hoangnam-f16b',              'ANDROID', NOW() - INTERVAL '20 days', NOW() - INTERVAL '2 days'),
(7, 11, 'fcm-token-thaibinh-a27c',              'ANDROID', NOW() - INTERVAL '30 days', NOW() - INTERVAL '1 day');

SELECT setval('notification_schema.fcm_tokens_id_seq', 7, true);


-- =====================================================================
-- 8) iot_db — thiết bị, log mở ô, chống dò PIN, trạng thái phần cứng
-- =====================================================================
\connect iot_db

TRUNCATE TABLE iot_schema.box_hardware_status, iot_schema.access_attempts,
               iot_schema.box_access_logs, iot_schema.device_statuses
  RESTART IDENTITY CASCADE;

INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at, created_at, updated_at) VALUES
(1, 'RPI-CAB-DEMO-01', 1, 'ONLINE',  NOW() - INTERVAL '30 seconds', NOW() - INTERVAL '400 days', NOW() - INTERVAL '30 seconds'),
(2, 'RPI-CAB-VGP-01',  2, 'ONLINE',  NOW() - INTERVAL '1 minute',   NOW() - INTERVAL '350 days', NOW() - INTERVAL '1 minute'),
(3, 'RPI-CAB-LM81-01', 3, 'ONLINE',  NOW() - INTERVAL '2 minutes',  NOW() - INTERVAL '300 days', NOW() - INTERVAL '2 minutes'),
(4, 'RPI-CAB-AEON-01', 4, 'OFFLINE', NOW() - INTERVAL '5 days',     NOW() - INTERVAL '260 days', NOW() - INTERVAL '5 days');

SELECT setval('iot_schema.device_statuses_id_seq', 4, true);

INSERT INTO iot_schema.box_access_logs
(id, box_id, locker_id, order_id, actor_user_id, credential_type, result, message, created_at)
VALUES
(1,  4,  1, 1,  2,  'PIN_OR_QR',   'SUCCESS', NULL,                              NOW() - INTERVAL '6 hours'),
(2,  7,  1, 2,  2,  'PIN_OR_QR',   'SUCCESS', NULL,                              NOW() - INTERVAL '20 hours'),
(3,  5,  1, 4,  2,  'PIN_OR_QR',   'SUCCESS', NULL,                              NOW() - INTERVAL '12 days'),
(4,  13, 2, 5,  2,  'PIN_OR_QR',   'SUCCESS', NULL,                              NOW() - INTERVAL '20 days'),
(5,  14, 2, 5,  5,  'ACCESS_CODE', 'SUCCESS', NULL,                              NOW() - INTERVAL '19 days'),
(6,  8,  1, NULL, 2, 'PIN_OR_QR',  'DENIED',  'Sai PIN',                         NOW() - INTERVAL '2 days 3 hours'),
(7,  8,  1, NULL, 2, 'PIN_OR_QR',  'DENIED',  'Sai PIN',                         NOW() - INTERVAL '2 days 3 hours'),
(8,  8,  1, NULL, 2, 'PIN_OR_QR',  'FAILED',  'Hardware failed to open',         NOW() - INTERVAL '2 days 2 hours'),
(9,  25, 3, NULL, 5, 'PIN_OR_QR',  'FAILED',  'Hardware failed to open',         NOW() - INTERVAL '8 days'),
(10, 25, 3, NULL, 11,'MASTER',     'SUCCESS', NULL,                              NOW() - INTERVAL '7 days'),
(11, 12, 2, 12, 10, 'ACCESS_CODE', 'SUCCESS', NULL,                              NOW() - INTERVAL '6 days'),
(12, 9,  1, 11, 8,  'PIN_OR_QR',   'SUCCESS', NULL,                              NOW() - INTERVAL '8 days'),
(13, 10, 1, 14, 12, 'PIN_OR_QR',   'DENIED',  'PIN đã hết hiệu lực',             NOW() - INTERVAL '24 days'),
(14, 10, 1, 14, 3,  'MASTER',      'SUCCESS', NULL,                              NOW() - INTERVAL '24 days'),
(15, 3,  1, 15, 2,  'ACCESS_CODE', 'SUCCESS', NULL,                              NOW() - INTERVAL '27 days'),
(16, 16, 2, 16, 6,  'PIN_OR_QR',   'SUCCESS', NULL,                              NOW() - INTERVAL '17 days'),
(17, 26, 3, 18, 8,  'PIN_OR_QR',   'SUCCESS', NULL,                              NOW() - INTERVAL '14 days'),
(18, 27, 3, 18, 12, 'ACCESS_CODE', 'SUCCESS', NULL,                              NOW() - INTERVAL '13 days'),
(19, 18, 2, 19, 12, 'PIN_OR_QR',   'SUCCESS', NULL,                              NOW() - INTERVAL '5 days'),
(20, 2,  1, 20, 6,  'ACCESS_CODE', 'SUCCESS', NULL,                              NOW() - INTERVAL '3 days'),
(21, 30, 4, 10, 7,  'PIN_OR_QR',   'SUCCESS', NULL,                              NOW() - INTERVAL '2 days'),
(22, 33, 4, NULL, 4, 'MASTER',     'TIMEOUT', 'Thiết bị không phản hồi',         NOW() - INTERVAL '5 days'),
(23, 9,  1, 24, 11, 'PIN_OR_QR',   'SUCCESS', NULL,                              NOW() - INTERVAL '2 days'),
(24, 22, 3, 23, 12, 'PIN_OR_QR',   'SUCCESS', NULL,                              NOW() - INTERVAL '8 days'),
(25, 2,  1, 22, 7,  'PIN_OR_QR',   'SUCCESS', NULL,                              NOW() - INTERVAL '22 days');

SELECT setval('iot_schema.box_access_logs_id_seq', 25, true);

-- Chống dò PIN: ô 8 đang bị khoá tạm, ô 25 và 33 có lịch sử sai
INSERT INTO iot_schema.access_attempts (box_id, failed_count, locked_until, updated_at) VALUES
(8,  3, NOW() + INTERVAL '10 minutes', NOW() - INTERVAL '2 days 2 hours'),
(25, 1, NULL,                          NOW() - INTERVAL '8 days'),
(10, 1, NULL,                          NOW() - INTERVAL '24 days'),
(33, 2, NULL,                          NOW() - INTERVAL '5 days');

-- Trạng thái phần cứng: một dòng cho mỗi ô (37 ô)
INSERT INTO iot_schema.box_hardware_status (box_id, locker_id, hw_state, last_reported_at, updated_at)
SELECT b.box_id, b.locker_id, b.hw_state, NOW() - (b.mins || ' minutes')::INTERVAL, NOW() - (b.mins || ' minutes')::INTERVAL
FROM (VALUES
  (1,1,'CLOSED',2),(2,1,'CLOSED',3),(3,1,'CLOSED',4),(4,1,'CLOSED',5),(5,1,'CLOSED',6),
  (6,1,'CLOSED',7),(7,1,'CLOSED',8),(8,1,'DOOR_STUCK',9),(9,1,'CLOSED',10),(10,1,'CLOSED',11),
  (11,2,'CLOSED',3),(12,2,'CLOSED',4),(13,2,'CLOSED',5),(14,2,'CLOSED',6),(15,2,'CLOSED',7),
  (16,2,'CLOSED',8),(17,2,'CLOSED',9),(18,2,'CLOSED',10),(19,2,'CLOSED',11),
  (20,3,'CLOSED',4),(21,3,'CLOSED',5),(22,3,'CLOSED',6),(23,3,'CLOSED',7),(24,3,'CLOSED',8),
  (25,3,'DOOR_STUCK',9),(26,3,'CLOSED',10),(27,3,'CLOSED',11),(28,3,'CLOSED',12),
  (29,4,'OFFLINE',7200),(30,4,'OFFLINE',7200),(31,4,'OFFLINE',7200),(32,4,'OFFLINE',7200),
  (33,4,'OFFLINE',7200),(34,4,'OFFLINE',7200),(35,4,'OFFLINE',7200),(36,4,'OFFLINE',7200),
  (37,4,'OFFLINE',7200)
) AS b(box_id, locker_id, hw_state, mins);


-- =====================================================================
-- 9) loyalty_db — điểm thưởng
-- =====================================================================
\connect loyalty_db

TRUNCATE TABLE loyalty_schema.point_transactions, loyalty_schema.loyalty_accounts
  RESTART IDENTITY CASCADE;

-- points của mỗi tài khoản = tổng point_transactions bên dưới
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at) VALUES
(1,  1,  0,   0, 'BRONZE',   NOW() - INTERVAL '400 days', NULL),
(2,  2,  410, 6, 'GOLD',     NOW() - INTERVAL '320 days', NOW() - INTERVAL '6 hours'),
(3,  3,  0,   0, 'BRONZE',   NOW() - INTERVAL '300 days', NULL),
(4,  4,  0,   0, 'BRONZE',   NOW() - INTERVAL '300 days', NULL),
(5,  5,  180, 3, 'SILVER',   NOW() - INTERVAL '210 days', NOW() - INTERVAL '2 hours'),
(6,  6,  270, 4, 'SILVER',   NOW() - INTERVAL '180 days', NOW() - INTERVAL '3 days'),
(7,  7,  200, 2, 'SILVER',   NOW() - INTERVAL '150 days', NOW() - INTERVAL '2 days'),
(8,  8,  93,  2, 'BRONZE',   NOW() - INTERVAL '120 days', NOW() - INTERVAL '8 days'),
(9,  9,  0,   0, 'BRONZE',   NOW() - INTERVAL '110 days', NULL),
(10, 10, 55,  1, 'BRONZE',   NOW() - INTERVAL '90 days',  NOW() - INTERVAL '6 days'),
(11, 11, 38,  1, 'BRONZE',   NOW() - INTERVAL '260 days', NOW() - INTERVAL '2 days'),
(12, 12, 320, 3, 'PLATINUM', NOW() - INTERVAL '60 days',  NOW() - INTERVAL '5 days');

SELECT setval('loyalty_schema.loyalty_accounts_id_seq', 12, true);

INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at) VALUES
-- user 2: 70+55+96+50+140+60 = 471; -6 (điều chỉnh) -55 (đổi quà) = 410
(1,  2, 4,  70,  'EARN',       NOW() - INTERVAL '12 days'),
(2,  2, 5,  55,  'EARN',       NOW() - INTERVAL '19 days'),
(3,  2, 6,  96,  'EARN',       NOW() - INTERVAL '34 days'),
(4,  2, 1,  50,  'EARN',       NOW() - INTERVAL '6 hours'),
(5,  2, 2,  140, 'EARN',       NOW() - INTERVAL '20 hours'),
(6,  2, 3,  60,  'EARN',       NOW() - INTERVAL '3 hours'),
(7,  2, NULL, -6, 'ADJUSTMENT', NOW() - INTERVAL '9 days'),
(8,  2, NULL, -55, 'REDEEM',    NOW() - INTERVAL '2 days'),
-- user 5: 52+80+48 = 180
(9,  5, 15, 52,  'EARN', NOW() - INTERVAL '27 days'),
(10, 5, 21, 80,  'EARN', NOW() - INTERVAL '2 hours'),
(11, 5, NULL, 48, 'EARN', NOW() - INTERVAL '60 days'),
-- user 6: 144+75+51 = 270
(12, 6, 16, 144, 'EARN', NOW() - INTERVAL '17 days'),
(13, 6, 20, 75,  'EARN', NOW() - INTERVAL '3 days'),
(14, 6, 9,  51,  'EARN', NOW() - INTERVAL '9 hours'),
-- user 7: 160+40 = 200
(15, 7, 10, 160, 'EARN', NOW() - INTERVAL '2 days'),
(16, 7, 22, 40,  'EARN', NOW() - INTERVAL '22 days'),
-- user 8: 45+48 = 93
(17, 8, 11, 45,  'EARN', NOW() - INTERVAL '8 days'),
(18, 8, 18, 48,  'EARN', NOW() - INTERVAL '13 days'),
-- user 10: 55
(19, 10, 12, 55, 'EARN', NOW() - INTERVAL '6 days'),
-- user 11: 38
(20, 11, 24, 38, 'EARN', NOW() - INTERVAL '2 days'),
-- user 12: 150+40+130 = 320
(21, 12, 14, 150, 'EARN', NOW() - INTERVAL '24 days'),
(22, 12, 19, 40,  'EARN', NOW() - INTERVAL '5 days'),
(23, 12, 23, 130, 'EARN', NOW() - INTERVAL '8 days');

SELECT setval('loyalty_schema.point_transactions_id_seq', 23, true);


-- =====================================================================
-- KIỂM TRA NHANH SAU KHI NẠP
-- =====================================================================
\connect postgres
\echo ''
\echo '=== ĐÃ NẠP XONG. Đếm bản ghi từng bảng: ==='
