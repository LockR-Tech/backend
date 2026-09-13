-- =====================================================================
-- KIỂM CHỨNG bộ seed local (chạy sau scripts/seed-local-complete.sql)
--   docker exec -i ll-ms-postgres psql -U postgres < scripts/verify-local-seed.sql
--
-- Mỗi mục in ra "OK" hoặc liệt kê dòng sai. Không có dòng sai = dữ liệu nhất quán.
-- =====================================================================

\pset pager off

\connect user_db
\echo ''
\echo '### user_db'
SELECT 'user_profiles' AS bang, count(*) AS so_dong FROM user_schema.user_profiles;
SELECT roles, count(*) FROM user_schema.user_profiles GROUP BY roles ORDER BY roles;

\connect auth_db
\echo ''
\echo '### auth_db — mỗi user phải có đúng 1 tài khoản đăng nhập'
SELECT 'auth_accounts' AS bang, count(*) FROM auth_schema.auth_accounts;
SELECT 'refresh_tokens' AS bang, count(*) FROM auth_schema.refresh_tokens;
SELECT 'email_otps' AS bang, count(*) FROM auth_schema.email_otps;
SELECT 'social_identities' AS bang, count(*) FROM auth_schema.social_identities;
\echo '-- LỖI nếu có dòng: refresh_token trỏ tới account không tồn tại'
SELECT r.id, r.account_id FROM auth_schema.refresh_tokens r
  LEFT JOIN auth_schema.auth_accounts a ON a.id = r.account_id WHERE a.id IS NULL;

\connect store_db
\echo ''
\echo '### store_db'
SELECT 'stores' AS bang, count(*) FROM store_schema.stores;

\connect locker_db
\echo ''
\echo '### locker_db'
SELECT 'lockers' AS bang, count(*) FROM locker_schema.lockers
UNION ALL SELECT 'locker_boxes', count(*) FROM locker_schema.locker_boxes
UNION ALL SELECT 'locker_reports', count(*) FROM locker_schema.locker_reports
UNION ALL SELECT 'repair_logs', count(*) FROM locker_schema.repair_logs
UNION ALL SELECT 'locker_report_ratings', count(*) FROM locker_schema.locker_report_ratings
UNION ALL SELECT 'maintenance_schedules', count(*) FROM locker_schema.maintenance_schedules
UNION ALL SELECT 'drone_units', count(*) FROM locker_schema.drone_units
UNION ALL SELECT 'drone_maintenance_logs', count(*) FROM locker_schema.drone_maintenance_logs
UNION ALL SELECT 'drone_delivery_requests', count(*) FROM locker_schema.drone_delivery_requests;

\echo '-- Phân bố trạng thái ô tủ'
SELECT status, count(*) FROM locker_schema.locker_boxes GROUP BY status ORDER BY status;
\echo '-- LỖI nếu có dòng: ô FAULT mà không ghi lý do'
SELECT id, box_number FROM locker_schema.locker_boxes WHERE status = 'FAULT' AND fault_reason IS NULL;
\echo '-- LỖI nếu có dòng: phiếu RESOLVED mà thiếu người xử lý hoặc thời điểm'
SELECT id FROM locker_schema.locker_reports
 WHERE status = 'RESOLVED' AND (resolved_by_user_id IS NULL OR resolved_at IS NULL);
\echo '-- LỖI nếu có dòng: repair_log trỏ tới phiếu không tồn tại'
SELECT l.id FROM locker_schema.repair_logs l
  LEFT JOIN locker_schema.locker_reports r ON r.id = l.report_id WHERE r.id IS NULL;
\echo '-- LỖI nếu có dòng: đánh giá phiếu mà phiếu chưa RESOLVED'
SELECT rr.id FROM locker_schema.locker_report_ratings rr
  JOIN locker_schema.locker_reports r ON r.id = rr.report_id WHERE r.status <> 'RESOLVED';

\connect order_db
\echo ''
\echo '### order_db'
SELECT 'orders' AS bang, count(*) FROM order_schema.orders
UNION ALL SELECT 'order_details', count(*) FROM order_schema.order_details
UNION ALL SELECT 'order_status_history', count(*) FROM order_schema.order_status_history
UNION ALL SELECT 'order_ratings', count(*) FROM order_schema.order_ratings
UNION ALL SELECT 'order_complaints', count(*) FROM order_schema.order_complaints
UNION ALL SELECT 'promotions', count(*) FROM order_schema.promotions
UNION ALL SELECT 'promotion_claims', count(*) FROM order_schema.promotion_claims
UNION ALL SELECT 'promotion_usages', count(*) FROM order_schema.promotion_usages
UNION ALL SELECT 'drone_missions', count(*) FROM order_schema.drone_missions;

\echo '-- Phân bố loại đơn / trạng thái'
SELECT type, status, count(*) FROM order_schema.orders GROUP BY type, status ORDER BY type, status;
\echo '-- LỖI nếu có dòng: đơn không có chi tiết'
SELECT o.id FROM order_schema.orders o
  LEFT JOIN order_schema.order_details d ON d.order_id = o.id WHERE d.id IS NULL;
\echo '-- LỖI nếu có dòng: đơn không có lịch sử trạng thái'
SELECT o.id FROM order_schema.orders o
  LEFT JOIN order_schema.order_status_history h ON h.order_id = o.id WHERE h.id IS NULL;
\echo '-- LỖI nếu có dòng: lịch sử mới nhất khác status hiện tại của đơn'
SELECT o.id, o.status AS status_don, h.new_status AS status_lich_su
  FROM order_schema.orders o
  JOIN LATERAL (SELECT new_status FROM order_schema.order_status_history
                 WHERE order_id = o.id ORDER BY created_at DESC, id DESC LIMIT 1) h ON TRUE
 WHERE o.status <> h.new_status;
\echo '-- LỖI nếu có dòng: đánh giá cho đơn chưa COMPLETED'
SELECT r.id, r.order_id FROM order_schema.order_ratings r
  JOIN order_schema.orders o ON o.id = r.order_id WHERE o.status <> 'COMPLETED';
\echo '-- LỖI nếu có dòng: usage_count của khuyến mãi sai so với số lần dùng thật'
SELECT p.id, p.code, p.usage_count AS ghi_nhan, count(u.id) AS thuc_te
  FROM order_schema.promotions p
  LEFT JOIN order_schema.promotion_usages u ON u.promotion_id = p.id
 GROUP BY p.id, p.code, p.usage_count HAVING p.usage_count <> count(u.id);
\echo '-- LỖI nếu có dòng: total_price khác original_price - discount'
SELECT id, order_code, original_price, discount, total_price
  FROM order_schema.orders WHERE total_price <> original_price - discount;
\echo '-- LỖI nếu có dòng: đơn DRONE_DELIVERY thiếu mission'
SELECT o.id FROM order_schema.orders o
  LEFT JOIN order_schema.drone_missions m ON m.order_id = o.id
 WHERE o.type = 'DRONE_DELIVERY' AND m.id IS NULL;

\connect payment_db
\echo ''
\echo '### payment_db'
SELECT 'payments' AS bang, count(*) FROM payment_schema.payments
UNION ALL SELECT 'refunds', count(*) FROM payment_schema.refunds
UNION ALL SELECT 'wallets', count(*) FROM payment_schema.wallets
UNION ALL SELECT 'wallet_transactions', count(*) FROM payment_schema.wallet_transactions;
SELECT method, status, count(*) FROM payment_schema.payments GROUP BY method, status ORDER BY method, status;
\echo '-- LỖI nếu có dòng: số dư ví khác tổng sổ cái'
SELECT w.user_id, w.balance AS so_du_vi,
       COALESCE(SUM(CASE WHEN t.type = 'CREDIT' THEN t.amount ELSE -t.amount END), 0) AS tong_so_cai
  FROM payment_schema.wallets w
  LEFT JOIN payment_schema.wallet_transactions t ON t.wallet_id = w.id
 GROUP BY w.user_id, w.balance
HAVING w.balance <> COALESCE(SUM(CASE WHEN t.type = 'CREDIT' THEN t.amount ELSE -t.amount END), 0);
\echo '-- LỖI nếu có dòng: balance_after không cộng dồn đúng theo thời gian'
WITH seq AS (
  SELECT id, wallet_id, balance_after,
         SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE -amount END)
           OVER (PARTITION BY wallet_id ORDER BY created_at, id
                 ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS tich_luy
    FROM payment_schema.wallet_transactions)
SELECT id, wallet_id, balance_after, tich_luy FROM seq WHERE balance_after <> tich_luy;
\echo '-- LỖI nếu có dòng: refund trỏ tới payment không tồn tại'
SELECT r.id FROM payment_schema.refunds r
  LEFT JOIN payment_schema.payments p ON p.id = r.payment_id WHERE p.id IS NULL;
\echo '-- LỖI nếu có dòng: refund lớn hơn số tiền đã thu'
SELECT r.id, r.amount, p.amount FROM payment_schema.refunds r
  JOIN payment_schema.payments p ON p.id = r.payment_id WHERE r.amount > p.amount;

\connect notification_db
\echo ''
\echo '### notification_db'
SELECT 'notifications' AS bang, count(*) FROM notification_schema.notifications
UNION ALL SELECT 'fcm_tokens', count(*) FROM notification_schema.fcm_tokens;
\echo '-- LỖI nếu có dòng: is_read và status không khớp'
SELECT id FROM notification_schema.notifications
 WHERE (is_read = TRUE AND status <> 'READ') OR (is_read = FALSE AND status <> 'UNREAD');
\echo '-- LỖI nếu có dòng: đã đọc mà không có read_at'
SELECT id FROM notification_schema.notifications WHERE is_read = TRUE AND read_at IS NULL;

\connect iot_db
\echo ''
\echo '### iot_db'
SELECT 'device_statuses' AS bang, count(*) FROM iot_schema.device_statuses
UNION ALL SELECT 'box_access_logs', count(*) FROM iot_schema.box_access_logs
UNION ALL SELECT 'access_attempts', count(*) FROM iot_schema.access_attempts
UNION ALL SELECT 'box_hardware_status', count(*) FROM iot_schema.box_hardware_status;
SELECT result, count(*) FROM iot_schema.box_access_logs GROUP BY result ORDER BY result;
SELECT hw_state, count(*) FROM iot_schema.box_hardware_status GROUP BY hw_state ORDER BY hw_state;

\connect loyalty_db
\echo ''
\echo '### loyalty_db'
SELECT 'loyalty_accounts' AS bang, count(*) FROM loyalty_schema.loyalty_accounts
UNION ALL SELECT 'point_transactions', count(*) FROM loyalty_schema.point_transactions;
\echo '-- LỖI nếu có dòng: điểm trong tài khoản khác tổng giao dịch điểm'
SELECT a.user_id, a.points AS diem_tai_khoan, COALESCE(SUM(t.points), 0) AS tong_giao_dich
  FROM loyalty_schema.loyalty_accounts a
  LEFT JOIN loyalty_schema.point_transactions t ON t.user_id = a.user_id
 GROUP BY a.user_id, a.points
HAVING a.points <> COALESCE(SUM(t.points), 0);

\echo ''
\echo '=== HẾT. Không thấy dòng nào dưới các dòng "LỖI nếu có dòng" = dữ liệu nhất quán. ==='
