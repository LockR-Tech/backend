-- =============================================
-- Seed Test User: binhtntse182370@fpt.edu.vn
-- Password: 12345678 (BCrypt hash)
-- =============================================
SET
search_path TO laundry_locker_schema;

-- 1. Create user account
INSERT INTO users (created_at, delete_flag, updated_at, birthday,
                   email, email_verified, enabled, first_name, image_url,
                   last_name, name, password, phone_number, phone_verified,
                   provider, provider_id)
VALUES (NOW() - INTERVAL '30 days', false, NOW(),
        '2000-05-15',
        'binhtntse182370@fpt.edu.vn', true, true,
        'Bình', 'https://ui-avatars.com/api/?name=Binh+Truong&background=4F46E5&color=fff&size=200',
        'Trương Nguyễn Thái', 'Bình Trương Nguyễn Thái',
        '$2a$10$S2LjQzym5drWNMKOdCpbWuZIKwJONRrabq54MiQnweVSvRlHMvf5O',
        '0869371051', true,
        'LOCAL', NULL) ON CONFLICT (email) DO
UPDATE SET
    password = EXCLUDED.password,
    enabled = true,
    email_verified = true,
    phone_verified = true,
    updated_at = NOW();

-- Get the user ID and insert all related data
DO
$$
DECLARE
v_user_id BIGINT;
    v_role_id
BIGINT;
    v_locker_id
BIGINT;
    v_box_id_1
BIGINT;
    v_box_id_2
BIGINT;
    v_box_id_3
BIGINT;
    v_order_id_1
BIGINT;
    v_order_id_2
BIGINT;
    v_order_id_3
BIGINT;
    v_order_id_4
BIGINT;
    v_order_id_5
BIGINT;
    v_service_id_1
BIGINT;
    v_service_id_2
BIGINT;
    v_service_id_3
BIGINT;
    v_store_id
BIGINT;
    v_staff_id
BIGINT;
    v_stamp_card_id
BIGINT;
BEGIN
    -- Get user ID
SELECT id
INTO v_user_id
FROM users
WHERE email = 'binhtntse182370@fpt.edu.vn';
RAISE
NOTICE 'User ID: %', v_user_id;

    -- 2. Assign USER role
SELECT id
INTO v_role_id
FROM roles
WHERE name = 'USER';
INSERT INTO user_roles (user_id, role_id)
VALUES (v_user_id, v_role_id) ON CONFLICT (user_id, role_id) DO NOTHING;

-- 3. Create loyalty account
INSERT INTO loyalty_accounts (created_at, delete_flag, updated_at,
                              points_balance, total_amount_spent, total_points_earned, total_points_redeemed,
                              user_id)
VALUES (NOW() - INTERVAL '30 days', false, NOW(),
        580, 725000.00, 725, 145,
        v_user_id) ON CONFLICT (user_id) DO
UPDATE SET
    points_balance = 580,
    total_amount_spent = 725000.00,
    total_points_earned = 725,
    total_points_redeemed = 145,
    updated_at = NOW();

-- 3.5. Create/Update wallet with 500,000 VND
INSERT INTO payment_schema.wallets (user_id, balance, currency, version, created_at, updated_at)
VALUES (v_user_id, 500000.00, 'VND', 1, NOW(), NOW()) ON CONFLICT (user_id) DO
UPDATE SET
    balance = 500000.00,
    updated_at = NOW(),
    version = payment_schema.wallets.version + 1;


-- Get references for orders
SELECT id
INTO v_locker_id
FROM lockers
WHERE status = 'ACTIVE' LIMIT 1;
SELECT id
INTO v_store_id
FROM stores
WHERE status = 'ACTIVE' LIMIT 1;
SELECT u.id
INTO v_staff_id
FROM users u
         JOIN user_roles ur ON u.id = ur.user_id
         JOIN roles r ON ur.role_id = r.id
WHERE r.name = 'ADMIN' LIMIT 1;

-- Get available boxes from the locker
SELECT id
INTO v_box_id_1
FROM boxes
WHERE locker_id = v_locker_id
  AND status = 'AVAILABLE' LIMIT 1
OFFSET 0;
SELECT id
INTO v_box_id_2
FROM boxes
WHERE locker_id = v_locker_id
  AND status = 'AVAILABLE' LIMIT 1
OFFSET 1;
SELECT id
INTO v_box_id_3
FROM boxes
WHERE locker_id = v_locker_id
  AND status = 'AVAILABLE' LIMIT 1
OFFSET 2;

-- Get service IDs
SELECT id
INTO v_service_id_1
FROM services
WHERE store_id = v_store_id
  AND category = 'STORAGE'
  AND status = 'ACTIVE' LIMIT 1;
SELECT id
INTO v_service_id_2
FROM services
WHERE store_id = v_store_id
  AND category = 'LAUNDRY'
  AND status = 'ACTIVE' LIMIT 1;
SELECT id
INTO v_service_id_3
FROM services
WHERE store_id = v_store_id
  AND category = 'LAUNDRY'
  AND status = 'ACTIVE' LIMIT 1
OFFSET 1;

-- ============================================
-- 4. Create 5 orders with different statuses
-- ============================================

-- Order 1: COMPLETED (Laundry - 25 days ago)
INSERT INTO orders (created_at, delete_flag, updated_at, order_code,
                    sender_id, receiver_id, receiver_name, receiver_phone,
                    locker_id, send_box_id, staff_id,
                    type, status,
                    actual_weight, weight_unit, total_price, original_price,
                    reservation_fee, storage_price, shipping_fee, extra_fee, discount,
                    failed_pin_attempts, pin_code, pin_code_issued_at,
                    completed_at, receive_at,
                    customer_note, description)
VALUES (NOW() - INTERVAL '25 days', false, NOW() - INTERVAL '22 days',
        'ORD-TEST-BINH-001',
        v_user_id, v_user_id, 'Bình Trương Nguyễn Thái', '0869371051',
        v_locker_id, v_box_id_1, v_staff_id,
        'LAUNDRY', 'COMPLETED',
        3.50, 'kg', 75000.00, 85000.00,
        5000.00, 0.00, 0.00, 0.00, 10000.00,
        0, '123456', NOW() - INTERVAL '25 days',
        NOW() - INTERVAL '22 days', NOW() - INTERVAL '23 days',
        'Giặt quần áo hàng ngày', 'Giặt sấy 3.5kg - Đơn hoàn thành') RETURNING id
INTO v_order_id_1;

-- Order 2: COMPLETED (Storage - 18 days ago)
INSERT INTO orders (created_at, delete_flag, updated_at, order_code,
                    sender_id, receiver_id, receiver_name, receiver_phone,
                    locker_id, send_box_id, staff_id,
                    type, status,
                    total_price, original_price,
                    reservation_fee, storage_price, shipping_fee, extra_fee, discount,
                    failed_pin_attempts, pin_code, pin_code_issued_at,
                    completed_at, receive_at,
                    customer_note, description)
VALUES (NOW() - INTERVAL '18 days', false, NOW() - INTERVAL '16 days',
        'ORD-TEST-BINH-002',
        v_user_id, v_user_id, 'Bình Trương Nguyễn Thái', '0869371051',
        v_locker_id, v_box_id_2, v_staff_id,
        'STORAGE', 'COMPLETED',
        15000.00, 15000.00,
        5000.00, 10000.00, 0.00, 0.00, 0.00,
        0, '234567', NOW() - INTERVAL '18 days',
        NOW() - INTERVAL '16 days', NOW() - INTERVAL '17 days',
        'Gửi túi laptop qua đêm', 'Gửi đồ storage - hoàn thành') RETURNING id
INTO v_order_id_2;

-- Order 3: COMPLETED (Laundry - 10 days ago)
INSERT INTO orders (created_at, delete_flag, updated_at, order_code,
                    sender_id, receiver_id, receiver_name, receiver_phone,
                    locker_id, send_box_id, staff_id,
                    type, status,
                    actual_weight, weight_unit, total_price, original_price,
                    reservation_fee, storage_price, shipping_fee, extra_fee, discount,
                    failed_pin_attempts, pin_code, pin_code_issued_at,
                    completed_at, receive_at,
                    customer_note, description)
VALUES (NOW() - INTERVAL '10 days', false, NOW() - INTERVAL '7 days',
        'ORD-TEST-BINH-003',
        v_user_id, v_user_id, 'Bình Trương Nguyễn Thái', '0869371051',
        v_locker_id, v_box_id_3, v_staff_id,
        'LAUNDRY', 'COMPLETED',
        5.00, 'kg', 125000.00, 150000.00,
        5000.00, 0.00, 0.00, 0.00, 25000.00,
        0, '345678', NOW() - INTERVAL '10 days',
        NOW() - INTERVAL '7 days', NOW() - INTERVAL '8 days',
        'Giặt chăn ga gối', 'Giặt hấp cao cấp 5kg - có mã giảm giá') RETURNING id
INTO v_order_id_3;

-- Order 4: PROCESSING (Laundry - 3 days ago)
INSERT INTO orders (created_at, delete_flag, updated_at, order_code,
                    sender_id, receiver_id, receiver_name, receiver_phone,
                    locker_id, send_box_id, staff_id,
                    type, status,
                    actual_weight, weight_unit, total_price, original_price,
                    reservation_fee, storage_price, shipping_fee, extra_fee, discount,
                    failed_pin_attempts, pin_code, pin_code_issued_at,
                    intended_receive_at,
                    customer_note, description)
VALUES (NOW() - INTERVAL '3 days', false, NOW() - INTERVAL '2 days',
        'ORD-TEST-BINH-004',
        v_user_id, v_user_id, 'Bình Trương Nguyễn Thái', '0869371051',
        v_locker_id, v_box_id_1, v_staff_id,
        'LAUNDRY', 'PROCESSING',
        2.00, 'kg', 24000.00, 24000.00,
        5000.00, 0.00, 0.00, 0.00, 0.00,
        0, '456789', NOW() - INTERVAL '3 days',
        NOW() + INTERVAL '1 day',
        'Giặt áo sơ mi, cẩn thận vải mỏng', 'Giặt sấy thường 2kg - đang xử lý') RETURNING id
INTO v_order_id_4;

-- Order 5: CANCELED (5 days ago)
INSERT INTO orders (created_at, delete_flag, updated_at, order_code,
                    sender_id, receiver_id, receiver_name, receiver_phone,
                    locker_id, send_box_id,
                    type, status,
                    total_price, original_price,
                    reservation_fee, storage_price, shipping_fee, extra_fee, discount,
                    failed_pin_attempts, cancel_reason,
                    customer_note, description)
VALUES (NOW() - INTERVAL '5 days', false, NOW() - INTERVAL '5 days',
        'ORD-TEST-BINH-005',
        v_user_id, v_user_id, 'Bình Trương Nguyễn Thái', '0869371051',
        v_locker_id, v_box_id_2,
        'STORAGE', 'CANCELED',
        5000.00, 5000.00,
        5000.00, 0.00, 0.00, 0.00, 0.00,
        0, 1,
        'Đổi lịch, không gửi nữa', 'Đơn hủy bởi khách hàng') RETURNING id
INTO v_order_id_5;

-- ============================================
-- 5. Create order details
-- ============================================
INSERT INTO order_details (created_at, delete_flag, updated_at, description, price, quantity, order_id, service_id)
VALUES (NOW() - INTERVAL '25 days', false, NOW(), 'Giặt sấy thường 3.5kg', 42000.00, 3.50, v_order_id_1,
        v_service_id_2),
       (NOW() - INTERVAL '18 days', false, NOW(), 'Gửi hàng thường', 5000.00, 1.00, v_order_id_2, v_service_id_1),
       (NOW() - INTERVAL '10 days', false, NOW(), 'Giặt hấp cao cấp 5kg', 125000.00, 5.00, v_order_id_3,
        v_service_id_3),
       (NOW() - INTERVAL '3 days', false, NOW(), 'Giặt sấy thường 2kg', 24000.00, 2.00, v_order_id_4, v_service_id_2),
       (NOW() - INTERVAL '5 days', false, NOW(), 'Gửi hàng thường (đã hủy)', 5000.00, 1.00, v_order_id_5,
        v_service_id_1);

-- ============================================
-- 6. Create order status history
-- ============================================
INSERT INTO order_status_history (created_at, delete_flag, updated_at,
                                  to_status, from_status, changed_at, changed_by, note, order_id)
VALUES
    -- Order 1 history (COMPLETED)
    (NOW() - INTERVAL '25 days', false, NOW(), 'INITIALIZED', NULL, NOW() - INTERVAL '25 days', v_user_id,
     'Khách hàng tạo đơn', v_order_id_1),
    (NOW() - INTERVAL '24 days', false, NOW(), 'COLLECTED', 'INITIALIZED', NOW() - INTERVAL '24 days', v_staff_id,
     'Nhân viên lấy đồ từ tủ', v_order_id_1),
    (NOW() - INTERVAL '23 days', false, NOW(), 'PROCESSING', 'COLLECTED', NOW() - INTERVAL '23 days', v_staff_id,
     'Đang giặt sấy', v_order_id_1),
    (NOW() - INTERVAL '22 days', false, NOW(), 'COMPLETED', 'PROCESSING', NOW() - INTERVAL '22 days', v_staff_id,
     'Hoàn thành đơn hàng', v_order_id_1),
    -- Order 2 history (COMPLETED)
    (NOW() - INTERVAL '18 days', false, NOW(), 'INITIALIZED', NULL, NOW() - INTERVAL '18 days', v_user_id,
     'Gửi đồ vào tủ', v_order_id_2),
    (NOW() - INTERVAL '16 days', false, NOW(), 'COMPLETED', 'INITIALIZED', NOW() - INTERVAL '16 days', v_user_id,
     'Lấy đồ thành công', v_order_id_2),
    -- Order 3 history (COMPLETED)
    (NOW() - INTERVAL '10 days', false, NOW(), 'INITIALIZED', NULL, NOW() - INTERVAL '10 days', v_user_id,
     'Tạo đơn giặt hấp', v_order_id_3),
    (NOW() - INTERVAL '9 days', false, NOW(), 'COLLECTED', 'INITIALIZED', NOW() - INTERVAL '9 days', v_staff_id,
     'Lấy đồ từ tủ', v_order_id_3),
    (NOW() - INTERVAL '8 days', false, NOW(), 'PROCESSING', 'COLLECTED', NOW() - INTERVAL '8 days', v_staff_id,
     'Giặt hấp đang xử lý', v_order_id_3),
    (NOW() - INTERVAL '7 days', false, NOW(), 'COMPLETED', 'PROCESSING', NOW() - INTERVAL '7 days', v_staff_id,
     'Hoàn thành giặt hấp', v_order_id_3),
    -- Order 4 history (PROCESSING)
    (NOW() - INTERVAL '3 days', false, NOW(), 'INITIALIZED', NULL, NOW() - INTERVAL '3 days', v_user_id, 'Đơn giặt mới',
     v_order_id_4),
    (NOW() - INTERVAL '2 days', false, NOW(), 'COLLECTED', 'INITIALIZED', NOW() - INTERVAL '2 days', v_staff_id,
     'Nhân viên lấy đồ', v_order_id_4),
    (NOW() - INTERVAL '2 days', false, NOW(), 'PROCESSING', 'COLLECTED', NOW() - INTERVAL '2 days', v_staff_id,
     'Đang xử lý giặt sấy', v_order_id_4),
    -- Order 5 history (CANCELED)
    (NOW() - INTERVAL '5 days', false, NOW(), 'INITIALIZED', NULL, NOW() - INTERVAL '5 days', v_user_id,
     'Tạo đơn gửi đồ', v_order_id_5),
    (NOW() - INTERVAL '5 days', false, NOW(), 'CANCELED', 'INITIALIZED', NOW() - INTERVAL '5 days', v_user_id,
     'Khách hàng hủy đơn', v_order_id_5);

-- ============================================
-- 7. Create order ratings (for completed orders)
-- ============================================
INSERT INTO order_ratings (created_at, delete_flag, updated_at, rating, comment, order_id, user_id)
VALUES (NOW() - INTERVAL '22 days', false, NOW(), 5, 'Dịch vụ tuyệt vời, quần áo thơm và sạch sẽ!', v_order_id_1,
        v_user_id),
       (NOW() - INTERVAL '7 days', false, NOW(), 4, 'Giặt hấp rất tốt, hơi lâu một chút nhưng chất lượng ổn',
        v_order_id_3, v_user_id);

-- ============================================
-- 8. Create payments (4 completed payments)
-- ============================================
INSERT INTO payments (created_at, delete_flag, updated_at,
                      amount, method, status, content, description,
                      reference_id, reference_transaction_id,
                      order_id, customer_id)
VALUES (NOW() - INTERVAL '25 days', false, NOW(), 75000.00, 'VNPAY', 'COMPLETED',
        'Thanh toán ORD-TEST-BINH-001', 'VNPay thanh toán giặt sấy',
        'PAY-TEST-BINH-001', 'VNPAY-TXN-BINH-001', v_order_id_1, v_user_id),
       (NOW() - INTERVAL '18 days', false, NOW(), 15000.00, 'VNPAY', 'COMPLETED',
        'Thanh toán ORD-TEST-BINH-002', 'VNPay thanh toán gửi đồ',
        'PAY-TEST-BINH-002', 'VNPAY-TXN-BINH-002', v_order_id_2, v_user_id),
       (NOW() - INTERVAL '10 days', false, NOW(), 125000.00, 'VNPAY', 'COMPLETED',
        'Thanh toán ORD-TEST-BINH-003', 'VNPay thanh toán giặt hấp',
        'PAY-TEST-BINH-003', 'VNPAY-TXN-BINH-003', v_order_id_3, v_user_id),
       (NOW() - INTERVAL '3 days', false, NOW(), 24000.00, 'VNPAY', 'COMPLETED',
        'Thanh toán ORD-TEST-BINH-004', 'VNPay thanh toán đơn giặt đang xử lý',
        'PAY-TEST-BINH-004', 'VNPAY-TXN-BINH-004', v_order_id_4, v_user_id);

-- ============================================
-- 9. Create notifications (status UNREAD/READ)
-- ============================================
INSERT INTO notifications (created_at, title, message, type, reference_id, reference_type,
                           status, read_at, user_id)
VALUES (NOW() - INTERVAL '25 days',
        'Đơn hàng đã được tạo', 'Đơn giặt sấy ORD-TEST-BINH-001 đã được tạo thành công.',
        'ORDER_STATUS', v_order_id_1, 'ORDER', 'READ', NOW() - INTERVAL '25 days', v_user_id),
       (NOW() - INTERVAL '22 days',
        'Đơn hàng hoàn thành', 'Đơn giặt sấy ORD-TEST-BINH-001 đã hoàn thành. Hãy đến lấy đồ!',
        'ORDER_STATUS', v_order_id_1, 'ORDER', 'READ', NOW() - INTERVAL '22 days', v_user_id),
       (NOW() - INTERVAL '18 days',
        'Gửi đồ thành công', 'Đồ của bạn đã được gửi vào tủ. Mã PIN: 234567',
        'ORDER_STATUS', v_order_id_2, 'ORDER', 'READ', NOW() - INTERVAL '18 days', v_user_id),
       (NOW() - INTERVAL '10 days',
        'Đơn giặt hấp đã tạo', 'Đơn giặt hấp cao cấp ORD-TEST-BINH-003 đang được xử lý.',
        'ORDER_STATUS', v_order_id_3, 'ORDER', 'READ', NOW() - INTERVAL '10 days', v_user_id),
       (NOW() - INTERVAL '7 days',
        'Giặt hấp hoàn thành', 'Đơn ORD-TEST-BINH-003 hoàn thành. Quần áo đã sạch sẽ!',
        'ORDER_STATUS', v_order_id_3, 'ORDER', 'READ', NOW() - INTERVAL '7 days', v_user_id),
       (NOW() - INTERVAL '3 days',
        'Đơn hàng đang xử lý', 'Đơn giặt ORD-TEST-BINH-004 đang được giặt sấy.',
        'ORDER_STATUS', v_order_id_4, 'ORDER', 'UNREAD', NULL, v_user_id),
       (NOW() - INTERVAL '1 day',
        'Khuyến mãi đặc biệt!', 'Giảm 20% cho đơn giặt sấy tiếp theo. Mã: SUMMER20',
        'PROMOTION', NULL, 'PROMOTION', 'UNREAD', NULL, v_user_id),
       (NOW() - INTERVAL '2 hours',
        'Tích điểm thành công', 'Bạn đã tích được 125 điểm từ đơn ORD-TEST-BINH-003.',
        'LOYALTY_REWARD', NULL, 'LOYALTY', 'UNREAD', NULL, v_user_id);

-- ============================================
-- 10. Create point transactions
-- ============================================
INSERT INTO point_transactions (created_at, delete_flag, updated_at,
                                points, balance_after, description, type, reference_id, related_amount,
                                user_id, order_id)
VALUES (NOW() - INTERVAL '30 days', false, NOW(), 200, 200, 'Thưởng chào mừng thành viên mới', 'BONUS', NULL, NULL,
        v_user_id, NULL),
       (NOW() - INTERVAL '22 days', false, NOW(), 75, 275, 'Tích điểm đơn ORD-TEST-BINH-001', 'EARN',
        'ORD-TEST-BINH-001', 75000.00, v_user_id, v_order_id_1),
       (NOW() - INTERVAL '16 days', false, NOW(), 15, 290, 'Tích điểm đơn ORD-TEST-BINH-002', 'EARN',
        'ORD-TEST-BINH-002', 15000.00, v_user_id, v_order_id_2),
       (NOW() - INTERVAL '14 days', false, NOW(), -145, 145, 'Đổi điểm giảm giá đơn hàng', 'REDEEM', NULL, NULL,
        v_user_id, NULL),
       (NOW() - INTERVAL '7 days', false, NOW(), 125, 270, 'Tích điểm đơn ORD-TEST-BINH-003', 'EARN',
        'ORD-TEST-BINH-003', 125000.00, v_user_id, v_order_id_3),
       (NOW() - INTERVAL '3 days', false, NOW(), 24, 294, 'Tích điểm đơn ORD-TEST-BINH-004', 'EARN',
        'ORD-TEST-BINH-004', 24000.00, v_user_id, v_order_id_4),
       (NOW() - INTERVAL '1 day', false, NOW(), 286, 580, 'Thưởng hoàn thành nhiều đơn', 'BONUS', NULL, NULL, v_user_id,
        NULL);

-- ============================================
-- 11. Create stamp card (BOX type - 4/6 stamps)
-- ============================================
INSERT INTO stamp_cards (created_at, delete_flag, updated_at,
                         stamp_type, box_size, current_stamps, stamps_required,
                         free_rewards_available, total_rewards_redeemed, total_stamps_earned,
                         user_id, service_id)
VALUES (NOW() - INTERVAL '25 days', false, NOW(),
        'BOX', 'MEDIUM', 4, 6,
        0, 0, 4,
        v_user_id, NULL) RETURNING id
INTO v_stamp_card_id;

-- ============================================
-- 12. Create stamp transactions
-- ============================================
INSERT INTO stamp_transactions (created_at, delete_flag, updated_at,
                                stamps, stamps_after, rewards_after, type, description,
                                discount_applied, user_id, order_id, stamp_card_id)
VALUES (NOW() - INTERVAL '22 days', false, NOW(), 1, 1, 0, 'EARN', 'Tem từ đơn giặt sấy ORD-TEST-BINH-001', NULL,
        v_user_id, v_order_id_1, v_stamp_card_id),
       (NOW() - INTERVAL '16 days', false, NOW(), 1, 2, 0, 'EARN', 'Tem từ đơn gửi đồ ORD-TEST-BINH-002', NULL,
        v_user_id, v_order_id_2, v_stamp_card_id),
       (NOW() - INTERVAL '7 days', false, NOW(), 1, 3, 0, 'EARN', 'Tem từ đơn giặt hấp ORD-TEST-BINH-003', NULL,
        v_user_id, v_order_id_3, v_stamp_card_id),
       (NOW() - INTERVAL '3 days', false, NOW(), 1, 4, 0, 'EARN', 'Tem từ đơn giặt ORD-TEST-BINH-004', NULL, v_user_id,
        v_order_id_4, v_stamp_card_id);

-- ============================================
-- 13. Create FCM token (for push notifications)
-- ============================================
INSERT INTO fcm_tokens (created_at, token, device_type, updated_at, user_id)
VALUES (NOW(), 'test-fcm-token-binh-android-' || v_user_id, 'ANDROID', NOW(), v_user_id) ON CONFLICT
ON CONSTRAINT uk_fcm_token DO NOTHING;

-- ============================================
-- Summary
-- ============================================
RAISE
NOTICE '';
    RAISE
NOTICE '============================================';
    RAISE
NOTICE '✅ User seeded successfully!';
    RAISE
NOTICE '============================================';
    RAISE
NOTICE '   User ID     : %', v_user_id;
    RAISE
NOTICE '   Email       : binhtntse182370@fpt.edu.vn';
    RAISE
NOTICE '   Password    : 12345678';
    RAISE
NOTICE '   Role        : USER';
    RAISE
NOTICE '   Orders      : 5 (3 COMPLETED, 1 PROCESSING, 1 CANCELED)';
    RAISE
NOTICE '   Loyalty Pts : 580';
    RAISE
NOTICE '   Stamp Card  : 4/6 stamps (BOX MEDIUM)';
    RAISE
NOTICE '   Notifications: 8 (5 read, 3 unread)';
    RAISE
NOTICE '   Payments    : 4 (all VNPAY COMPLETED)';
    RAISE
NOTICE '   Ratings     : 2 (5★ and 4★)';
    RAISE
NOTICE '============================================';

END $$;
