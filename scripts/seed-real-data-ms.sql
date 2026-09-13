-- =====================================================================
-- Smart Laundry Locker — FULL DEMO SEED (microservices stack)
-- =====================================================================
-- Idempotent: safe to run repeatedly. Demo rows live in reserved id ranges
-- (users/stores/lockers/reports 9001-9200, orders/payments 90001-90100) and
-- recognizable markers (ORD-DEMO-*, PAY-DEMO-*, LCK-DEMO-*, *@demo.laundry.test),
-- so it only touches its own data and never collides with real records.
--
-- Run as superuser (switches DBs with \connect):
--   psql -h <host> -p <port> -U postgres -f scripts/seed-full-demo-ms.sql
--
-- 4 named accounts (password = 12345678, bcrypt strength 10):
--   ADMIN       baohuy2k12k4@gmail.com                user_id 9001
--   CUSTOMER    nqbhuy2004nt@gmail.com                user_id 9002
--   MAINTENANCE se180211nguyenquocbaohuy@gmail.com    user_id 9003
--   MANAGER     huynqbse180211@fpt.edu.vn             user_id 9004
-- + 100 bulk customers (user_id 9101-9200, *@demo.laundry.test)
-- =====================================================================

\set
pw '$2a$10$S2LjQzym5drWNMKOdCpbWuZIKwJONRrabq54MiQnweVSvRlHMvf5O'

-- =====================================================================
-- 1) user_db.user_profiles
-- =====================================================================
\connect user_db

DELETE
FROM user_schema.user_profiles
WHERE id BETWEEN 9001 AND 9200
   OR email IN ('baohuy2k12k4@gmail.com', 'nqbhuy2004nt@gmail.com',
                'se180211nguyenquocbaohuy@gmail.com', 'huynqbse180211@fpt.edu.vn')
   OR email LIKE '%@demo.laundry.test';

INSERT INTO user_schema.user_profiles
(id, email, phone_number, first_name, last_name, birthday, image_url, status, roles, created_at, updated_at)
VALUES (9001, 'baohuy2k12k4@gmail.com', '0980000001', 'Bảo Huy', 'Quản trị viên', DATE '1990-01-01', NULL, 'ACTIVE',
        'ADMIN', NOW(), NOW()),
       (9002, 'nqbhuy2004nt@gmail.com', '0980000002', 'Quốc Bảo', 'Huy', DATE '2004-04-04', NULL, 'ACTIVE', 'CUSTOMER',
        NOW(), NOW()),
       (9003, 'se180211nguyenquocbaohuy@gmail.com', '0980000003', 'Bảo Huy', 'Bảo Trì', DATE '1998-02-02', NULL,
        'ACTIVE', 'MAINTENANCE', NOW(), NOW()),
       (9004, 'huynqbse180211@fpt.edu.vn', '0980000004', 'Huy', 'Quản Lý', DATE '1995-03-03', NULL, 'ACTIVE', 'MANAGER',
        NOW(), NOW());

INSERT INTO user_schema.user_profiles
(id, email, phone_number, first_name, last_name, birthday, image_url, status, roles, created_at, updated_at)
SELECT 9100 + g,
       'khachhang' || g || '@gmail.com',
       '098' || lpad((1000000 + g)::text, 7, '0'),
       (ARRAY['Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Huỳnh', 'Phan', 'Vũ', 'Võ', 'Đặng'])[1 + (g % 10)], (ARRAY['Minh Anh', 'Thị Bé', 'Văn Chung', 'Hữu Đạt', 'Thanh Tùng', 'Thị Gái', 'Hải Hoàng', 'Văn Tuấn', 'Bảo Ngọc', 'Quốc Bảo'])[1 + (g % 10)],
       DATE '1995-01-01' + g,
       'https://ui-avatars.com/api/?name=KH' || g || '&background=4F46E5&color=fff',
       'ACTIVE', 'CUSTOMER',
       NOW() - (g || ' days')::interval, NOW()
FROM generate_series(1, 100) AS g;

-- =====================================================================
-- 2) auth_db.auth_accounts  (login: email + bcrypt(12345678))
-- =====================================================================
\connect
auth_db

DELETE
FROM auth_schema.auth_accounts
WHERE user_id BETWEEN 9001 AND 9200
   OR email IN ('baohuy2k12k4@gmail.com', 'nqbhuy2004nt@gmail.com',
                'se180211nguyenquocbaohuy@gmail.com', 'huynqbse180211@fpt.edu.vn')
   OR email LIKE '%@demo.laundry.test';

INSERT INTO auth_schema.auth_accounts
(user_id, email, phone_number, password_hash, auth_provider, email_verified, phone_verified, status, last_login_at,
 created_at, updated_at)
VALUES (9001, 'baohuy2k12k4@gmail.com', '0980000001', :'pw', 'LOCAL', TRUE, TRUE, 'ACTIVE', NOW(), NOW(), NOW()),
       (9002, 'nqbhuy2004nt@gmail.com', '0980000002', :'pw', 'LOCAL', TRUE, TRUE, 'ACTIVE', NOW(), NOW(), NOW()),
       (9003, 'se180211nguyenquocbaohuy@gmail.com', '0980000003', :'pw', 'LOCAL', TRUE, TRUE, 'ACTIVE', NOW(), NOW(),
        NOW()),
       (9004, 'huynqbse180211@fpt.edu.vn', '0980000004', :'pw', 'LOCAL', TRUE, TRUE, 'ACTIVE', NOW(), NOW(), NOW());

INSERT INTO auth_schema.auth_accounts
(user_id, email, phone_number, password_hash, auth_provider, email_verified, phone_verified, status, created_at,
 updated_at)
SELECT 9100 + g,
       'khachhang' || g || '@gmail.com',
       '098' || lpad((1000000 + g)::text, 7, '0'),
       :'pw', 'LOCAL',
       TRUE,
       TRUE,
       'ACTIVE',
       NOW() - (g || ' days')::interval, NOW()
FROM generate_series(1, 100) AS g;

-- =====================================================================
-- 3) store_db.stores  (id 9001-9100)
-- =====================================================================
\connect
store_db

DELETE
FROM store_schema.stores
WHERE id BETWEEN 9001 AND 9100;

INSERT INTO store_schema.stores
(id, name, contact_phone, address, latitude, longitude, image, description, is_active, status, created_at, updated_at)
SELECT 9000 + g,
       (ARRAY['Giặt Sấy Tiện Lợi', 'Locker Thông Minh', 'Giặt Sấy 24/7', 'Clean & Go', 'Giặt Sấy Nhanh'])[1 + (g % 5)] || ' - CN ' || g,
       '028' || lpad(g::text, 7, '0'),
       'Số ' || g || ' Nguyễn Văn Cừ, Quận ' || (1 + (g % 12)) || ', TP.HCM',
       10.7600 + (g % 50) * 0.0020,
       106.6600 + (g % 50) * 0.0020,
       NULL,
       'Cửa hàng giặt sấy và tủ khóa thông minh chi nhánh ' || g,
       (g % 10 <> 0),
       CASE WHEN g % 10 = 0 THEN 'INACTIVE' ELSE 'ACTIVE'
END,
       NOW() - (g || ' days')::interval, NOW()
FROM generate_series(1, 100) AS g;

-- =====================================================================
-- 4) locker_db  (lockers 9001-9100, boxes, reports 9001-9100, schedules, repair logs)
-- =====================================================================
\connect
locker_db

DELETE
FROM locker_schema.repair_logs
WHERE report_id BETWEEN 9001 AND 9100;
DELETE
FROM locker_schema.locker_reports
WHERE id BETWEEN 9001 AND 9100;
DELETE
FROM locker_schema.maintenance_schedules
WHERE locker_id BETWEEN 9001 AND 9100;
DELETE
FROM locker_schema.locker_boxes
WHERE locker_id BETWEEN 9001 AND 9100;
DELETE
FROM locker_schema.lockers
WHERE id BETWEEN 9001 AND 9100;

INSERT INTO locker_schema.lockers
(id, store_id, code, name, status, address, latitude, longitude, description, created_at, updated_at, landing_pad,
 landing_marker_id)
SELECT 9000 + g,
       9000 + g,
       'LCK-HCM-' || lpad(g::text, 3, '0'),
       'Tủ Giặt ' || g,
       CASE WHEN g % 11 = 0 THEN 'MAINTENANCE' ELSE 'ACTIVE' END,
       'Số ' || g || ' Nguyễn Văn Cừ, Quận ' || (1 + (g % 12)) || ', TP.HCM',
       10.7600 + (g % 50) * 0.0020,
       106.6600 + (g % 50) * 0.0020,
       'Tủ khóa thông minh tự phục vụ số ' || g,
       NOW() - (g || ' days')::interval, NOW(),
       (g % 5 = 0),
       CASE WHEN g % 5 = 0 THEN 'ARUCO-' || g ELSE NULL END
FROM generate_series(1, 100) AS g;

-- 9 cells per locker = 900 boxes (3 DRONE / 5 STANDARD / 1 XL layout)
INSERT INTO locker_schema.locker_boxes
(locker_id, box_number, size, is_active, description, status, created_at, updated_at, cell_type, row_index, col_index,
 fault_reason)
SELECT l,
       b,
       CASE WHEN b = 9 THEN 'LARGE' WHEN b <= 3 THEN 'SMALL' ELSE 'MEDIUM' END,
       TRUE,
       'Ô tủ số ' || b,
       CASE
           WHEN (l + b) % 17 = 0 THEN 'FAULT'
           WHEN (l + b) % 4  = 0 THEN 'OCCUPIED'
           WHEN (l + b) % 7  = 0 THEN 'RESERVED'
           ELSE 'AVAILABLE' END,
       NOW(),
       NOW(),
       CASE WHEN b <= 3 THEN 'DRONE' WHEN b = 9 THEN 'XL' ELSE 'STANDARD' END,
       (b - 1) / 3,
       (b - 1) % 3,
       CASE WHEN (l + b) % 17 = 0 THEN 'Hỏng khóa điện tử' ELSE NULL
END
FROM generate_series(9001, 9100) AS l, generate_series(1, 9) AS b;

INSERT INTO locker_schema.locker_reports
(id, locker_id, user_id, title, description, status, resolved_by_user_id, resolved_at, created_at, updated_at, box_id,
 assigned_to_user_id, assigned_at)
SELECT 9000 + g,
       9000 + g,
       9002,
       'Báo lỗi tủ LCK-HCM-' || lpad((g)::text, 3, '0'),
       'Ô tủ không mở được cửa, cần kiểm tra gấp, sự cố số ' || g,
       CASE WHEN g % 3 = 0 THEN 'RESOLVED' WHEN g % 3 = 1 THEN 'IN_PROGRESS' ELSE 'OPEN' END,
       CASE WHEN g % 3 = 0 THEN 9003 ELSE NULL END,
       CASE WHEN g % 3 = 0 THEN NOW() - (g || ' hours')::interval ELSE NULL END,
       NOW() - (g || ' days')::interval, NOW(),
       NULL,
       CASE WHEN g % 3 <> 2 THEN 9003 ELSE NULL
END,
       CASE WHEN g % 3 <> 2 THEN NOW() - (g || ' hours')::interval ELSE NULL
END
FROM generate_series(1, 100) AS g;

INSERT INTO locker_schema.repair_logs (report_id, actor_user_id, note, created_at)
SELECT 9000 + g,
       9003,
       'Bước xử lý ' || g || ': đã đến tận nơi kiểm tra, thay thế linh kiện khóa và test lại thành công.',
       NOW() - (g || ' hours') ::interval
FROM generate_series(1, 100) AS g;

INSERT INTO locker_schema.maintenance_schedules
(locker_id, title, interval_days, last_done_at, next_due_at, active, created_at, updated_at)
SELECT 9000 + g,
       'Bảo trì định kỳ tủ LCK-HCM-' || lpad(g::text, 3, '0'),
       CASE WHEN g % 2 = 0 THEN 30 ELSE 7 END,
       NOW() - ((g % 30) || ' days')::interval, NOW() + (((g % 15) - 7) || ' days')::interval, TRUE,
       NOW(),
       NOW()
FROM generate_series(1, 100) AS g;

-- =====================================================================
-- 5) order_db  (promotions, orders 90001-90100, details, history, ratings, complaints)
-- =====================================================================
\connect
order_db

DELETE
FROM order_schema.order_details
WHERE order_id BETWEEN 90001 AND 90100;
DELETE
FROM order_schema.order_status_history
WHERE order_id BETWEEN 90001 AND 90100;
DELETE
FROM order_schema.order_ratings
WHERE order_id BETWEEN 90001 AND 90100;
DELETE
FROM order_schema.order_complaints
WHERE order_id BETWEEN 90001 AND 90100;
DELETE
FROM order_schema.orders
WHERE id BETWEEN 90001 AND 90100;
DELETE
FROM order_schema.promotions
WHERE code LIKE 'PROMO-HCM-%';

INSERT INTO order_schema.promotions
(code, name, discount_type, discount_value, max_discount_amount, min_order_amount, stackable, status, start_at, end_at,
 usage_count, created_by_user_id, created_at, updated_at)
SELECT 'PROMO-HCM-' || lpad(g::text, 3, '0'),
       'Khuyến mãi tri ân khách hàng ' || g,
       CASE WHEN g % 2 = 0 THEN 'PERCENTAGE' ELSE 'FIXED_AMOUNT' END,
       CASE WHEN g % 2 = 0 THEN (5 + (g % 20)) ELSE (5000 + (g % 10) * 1000) END,
       CASE WHEN g % 2 = 0 THEN 50000 ELSE NULL END,
       (g % 5) * 10000,
       (g % 3 = 0),
       CASE WHEN g % 4 = 0 THEN 'INACTIVE' ELSE 'ACTIVE' END,
       NOW() - (g || ' days')::interval, NOW() + ((100 - g) || ' days')::interval, g % 50, 9001, NOW(), NOW()
FROM generate_series(1, 100) AS g;

INSERT INTO order_schema.orders
(id, order_code, user_id, receiver_id, receiver_phone, receiver_name, locker_id, store_id,
 type, service_category, status, actual_weight, weight_unit,
 total_price, original_price, reservation_fee, storage_price, shipping_fee, extra_fee, discount,
 pin_code, pin_code_issued_at, completed_at, created_at, updated_at, customer_note, description)
SELECT 90000 + g,
       'ORD-HCM-' || lpad(g::text, 5, '0'),
       9002,
       9002,
       '0980000002',
       'Nguyễn Quốc Bảo Huy',
       9001 + (g % 100),
       9001 + (g % 100),
       (ARRAY['STORAGE', 'LAUNDRY', 'SEND', 'RENTAL'])[1 + (g % 4)],
       (ARRAY['STORAGE','LAUNDRY','STORAGE','STORAGE'])[1 + (g % 4)],
       (ARRAY['COMPLETED','PROCESSING','INITIALIZED','CANCELED','COMPLETED','OCCUPIED'])[1 + (g % 6)],
       CASE WHEN (g % 4) = 1 THEN (1 + (g % 5))::numeric ELSE NULL
END, 'kg',
       (20000 + (g % 10) * 5000)::numeric, (25000 + (g % 10) * 5000)::numeric,
       5000, (g % 3) * 5000, 0, 0, (g % 4) * 2000,
       lpad((100000 + g)::text, 6, '0'), NOW() - (g || ' days')::interval,
       CASE WHEN (g % 6) IN (0, 4) THEN NOW() - ((g) || ' days')::interval + INTERVAL '2 days' ELSE NULL
END,
       NOW() - (g || ' days')::interval, NOW(),
       'Khách hàng gửi đồ vào buổi sáng, lấy vào buổi chiều.', 'Đơn hàng dịch vụ giặt sấy số ' || g
FROM generate_series(1, 100) AS g;

INSERT INTO order_schema.order_details (order_id, service_id, quantity, price, description)
SELECT 90000 + g,
       1 + (g % 5),
       1 + (g % 3),
       (20000 + (g % 5) * 5000)::numeric, 'Dịch vụ giặt sấy cho đơn ORD-HCM-' || lpad(g::text, 5, '0')
FROM generate_series(1, 100) AS g;

INSERT INTO order_schema.order_status_history (order_id, old_status, new_status, changed_by_user_id, note, created_at)
SELECT 90000 + g, NULL, 'INITIALIZED', 9002, 'Khách hàng tạo đơn', NOW() - (g || ' days') ::interval
FROM generate_series(1, 100) AS g
UNION ALL
SELECT 90000 + g, 'INITIALIZED', 'PROCESSING', 9004, 'Bắt đầu xử lý', NOW() - ((g) || ' days')::interval + INTERVAL '1 day'
FROM generate_series(1, 100) AS g;

INSERT INTO order_schema.order_ratings (order_id, user_id, rating, comment, created_at)
SELECT 90000 + g, 9002, 1 + (g % 5), 'Dịch vụ rất tốt, lấy đồ nhanh và tủ sạch sẽ.', NOW() - (g || ' days') ::interval
FROM generate_series(1, 100) AS g;

INSERT INTO order_schema.order_complaints (order_id, user_id, type, description, status, created_at)
SELECT 90000 + g,
       9002,
       (ARRAY['DAMAGE', 'LATE', 'LOST', 'OTHER'])[1 + (g % 4)],
       'Đồ bị nhăn một chút nhưng dịch vụ nhìn chung ổn.',
       (ARRAY['OPEN','RESOLVED','OPEN'])[1 + (g % 3)],
       NOW() - (g || ' days')::interval
FROM generate_series(1, 100) AS g;

-- =====================================================================
-- 6) payment_db  (payments 90001-90100, refunds)
-- =====================================================================
\connect
payment_db

DELETE
FROM payment_schema.refunds
WHERE transaction_id LIKE 'RF-DEMO-%';
DELETE
FROM payment_schema.payments
WHERE id BETWEEN 90001 AND 90100;

INSERT INTO payment_schema.payments
(id, order_id, user_id, amount, method, status, reference_id, reference_transaction_id, content, description,
 created_at, updated_at)
SELECT 90000 + g,
       90000 + g,
       9002,
       (20000 + (g % 10) * 5000)::numeric, (ARRAY['CASH', 'VNPAY', 'MOMO'])[1 + (g % 3)],
       (ARRAY['COMPLETED','PENDING','COMPLETED','FAILED'])[1 + (g % 4)],
       'PAY-HCM-' || lpad(g::text, 5, '0'),
       'TXN-HCM-' || lpad(g::text, 5, '0'),
       'Thanh toán đơn ORD-HCM-' || lpad(g::text, 5, '0'),
       'Giao dịch thanh toán tự động số ' || g,
       NOW() - (g || ' days')::interval, NOW()
FROM generate_series(1, 100) AS g;

INSERT INTO payment_schema.refunds
(payment_id, order_id, amount, status, reason, transaction_id, processed_by_user_id, requested_at, processed_at)
SELECT 90000 + g,
       90000 + g,
       (10000 + (g % 5) * 5000)::numeric, (ARRAY['PENDING', 'COMPLETED', 'REJECTED'])[1 + (g % 3)],
       'Hoàn tiền cho đơn ' || g || ' do khách huỷ',
       'RF-HCM-' || lpad(g::text, 5, '0'),
       9001, NOW() - (g || ' days')::interval,
       CASE WHEN (g % 3) = 1 THEN NOW() - ((g) || ' days')::interval + INTERVAL '1 day' ELSE NULL
END
FROM generate_series(1, 100) AS g;

-- =====================================================================
-- 7) notification_db  (notifications for user 9002, fcm tokens)
-- =====================================================================
\connect
notification_db

DELETE
FROM notification_schema.notifications
WHERE title LIKE 'Thong bao demo %';
DELETE
FROM notification_schema.fcm_tokens
WHERE token LIKE 'fcm-token-%';

INSERT INTO notification_schema.notifications
(user_id, title, message, type, reference_id, reference_type, status, is_read, read_at, created_at)
SELECT 9002,
       'Cập nhật trạng thái đơn hàng ' || g,
       'Đơn hàng của bạn đã được cập nhật trạng thái mới nhất.',
       (ARRAY['ORDER_STATUS', 'PROMOTION', 'LOYALTY_REWARD', 'SYSTEM'])[1 + (g % 4)],
       90000 + g, 'ORDER',
       CASE WHEN g % 2 = 0 THEN 'READ' ELSE 'UNREAD'
END,
       (g % 2 = 0),
       CASE WHEN g % 2 = 0 THEN NOW() - (g || ' hours')::interval ELSE NULL
END,
       NOW() - (g || ' hours')::interval
FROM generate_series(1, 100) AS g;

INSERT INTO notification_schema.fcm_tokens (user_id, token, device_type, created_at, updated_at)
SELECT 9100 + g, 'fcm-token-' || (9100 + g) || '-android', 'ANDROID', NOW(), NOW()
FROM generate_series(1, 100) AS g;

-- =====================================================================
-- 8) loyalty_db  (loyalty_accounts for all demo users, point_transactions)
-- =====================================================================
\connect
loyalty_db

DELETE
FROM loyalty_schema.point_transactions
WHERE user_id BETWEEN 9001 AND 9200;
DELETE
FROM loyalty_schema.loyalty_accounts
WHERE user_id BETWEEN 9001 AND 9200;

INSERT INTO loyalty_schema.loyalty_accounts (user_id, points, stamps, tier, created_at, updated_at)
VALUES (9001, 100, 0, 'BRONZE', NOW(), NOW()),
       (9002, 1250, 6, 'GOLD', NOW(), NOW()),
       (9003, 300, 1, 'SILVER', NOW(), NOW()),
       (9004, 500, 2, 'SILVER', NOW(), NOW());

INSERT INTO loyalty_schema.loyalty_accounts (user_id, points, stamps, tier, created_at, updated_at)
SELECT 9100 + g,
       (g * 13) % 2000, g % 10,
       (ARRAY['BRONZE','SILVER','GOLD'])[1 + (g % 3)], NOW(), NOW()
FROM generate_series(1, 100) AS g;

INSERT INTO loyalty_schema.point_transactions (user_id, order_id, points, type, created_at)
SELECT 9002,
       90000 + g,
       CASE WHEN g % 4 = 0 THEN -((g % 5 + 1) * 10) ELSE (g % 10 + 1) * 10 END,
       CASE WHEN g % 4 = 0 THEN 'REDEEM' ELSE 'EARN' END,
       NOW() - (g || ' days') ::interval
FROM generate_series(1, 100) AS g;

-- =====================================================================
-- 9) iot_db.device_statuses  (one device per demo locker)
-- =====================================================================
\connect
iot_db

DELETE
FROM iot_schema.device_statuses
WHERE device_id LIKE 'DEV-HCM-%';

INSERT INTO iot_schema.device_statuses (device_id, locker_id, status, last_seen_at, created_at, updated_at)
SELECT 'DEV-HCM-' || lpad(g::text, 3, '0'),
       9000 + g,
       CASE WHEN g % 10 = 0 THEN 'OFFLINE' ELSE 'ONLINE' END,
       NOW() - ((g % 60) || ' minutes')::interval, NOW(),
       NOW()
FROM generate_series(1, 100) AS g;

\echo
'====================================================================='
\echo ' FULL DEMO SEED applied.'
\echo '   4 accounts (pw 12345678): admin/customer/maintenance/manager + 100 bulk'
\echo '   stores 100, lockers 100, boxes 900, orders 100, payments 100, etc.'
\echo '====================================================================='
