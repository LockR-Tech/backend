\set
ON_ERROR_STOP on

\echo 'Seeding user_db'
\connect user_db

INSERT INTO user_schema.user_profiles (
    id, email, phone_number, first_name, last_name, birthday, image_url, status, roles, created_at, updated_at
) VALUES
    (1001, 'customer.seed@laundry.test', '0901001001', 'Demo', 'Customer', DATE '1999-01-15', 'https://ui-avatars.com/api/?name=Demo+Customer', 'ACTIVE', 'USER', NOW() - INTERVAL '20 days', NOW()),
    (1002, 'staff.seed@laundry.test', '0901001002', 'Demo', 'Staff', DATE '1997-04-10', 'https://ui-avatars.com/api/?name=Demo+Staff', 'ACTIVE', 'STAFF', NOW() - INTERVAL '18 days', NOW()),
    (1004, 'admin.seed@laundry.test', '0901001004', 'Demo', 'Admin', DATE '1990-12-02', 'https://ui-avatars.com/api/?name=Demo+Admin', 'ACTIVE', 'ADMIN', NOW() - INTERVAL '16 days', NOW()),
    (1005, 'customer.vip@laundry.test', '0901001005', 'Vip', 'Customer', DATE '1995-06-30', 'https://ui-avatars.com/api/?name=Vip+Customer', 'ACTIVE', 'USER', NOW() - INTERVAL '12 days', NOW())
ON CONFLICT (id) DO
UPDATE SET
    email = EXCLUDED.email,
    phone_number = EXCLUDED.phone_number,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    birthday = EXCLUDED.birthday,
    image_url = EXCLUDED.image_url,
    status = EXCLUDED.status,
    roles = EXCLUDED.roles,
    updated_at = NOW();

-- PA3: bỏ seed roles/permissions/role_permissions — các bảng này đã được drop
-- (user-service V3). Phân quyền dùng cột user_schema.user_profiles.roles (VARCHAR)
-- + claim `roles` trong JWT.

-- PA3 (đợt 2): bỏ seed audit_logs — bảng đã được drop (user-service V4).

SELECT setval(pg_get_serial_sequence('user_schema.user_profiles', 'id'),
              GREATEST((SELECT MAX(id) FROM user_schema.user_profiles), 1), true);

\echo
'Seeding auth_db'
\connect auth_db

INSERT INTO auth_schema.auth_accounts (
    user_id, email, phone_number, password_hash, auth_provider, email_verified, phone_verified, status, last_login_at, created_at, updated_at
) VALUES
    (1001, 'customer.seed@laundry.test', '0901001001', '$2a$10$iYGwq/z2LBYxTd92vFkTieFp8ZUrE.PHapoOqFETmA6tAAZCOXyfS', 'LOCAL', TRUE, TRUE, 'ACTIVE', NOW() - INTERVAL '2 days', NOW() - INTERVAL '20 days', NOW()),
    (1002, 'staff.seed@laundry.test', '0901001002', '$2a$10$iYGwq/z2LBYxTd92vFkTieFp8ZUrE.PHapoOqFETmA6tAAZCOXyfS', 'LOCAL', TRUE, TRUE, 'ACTIVE', NOW() - INTERVAL '3 days', NOW() - INTERVAL '18 days', NOW()),
    (1004, 'admin.seed@laundry.test', '0901001004', '$2a$10$iYGwq/z2LBYxTd92vFkTieFp8ZUrE.PHapoOqFETmA6tAAZCOXyfS', 'LOCAL', TRUE, TRUE, 'ACTIVE', NOW() - INTERVAL '1 day', NOW() - INTERVAL '16 days', NOW()),
    (1005, 'customer.vip@laundry.test', '0901001005', '$2a$10$iYGwq/z2LBYxTd92vFkTieFp8ZUrE.PHapoOqFETmA6tAAZCOXyfS', 'LOCAL', TRUE, TRUE, 'ACTIVE', NULL, NOW() - INTERVAL '12 days', NOW())
ON CONFLICT (user_id) DO
UPDATE SET
    email = EXCLUDED.email,
    phone_number = EXCLUDED.phone_number,
    password_hash = EXCLUDED.password_hash,
    auth_provider = EXCLUDED.auth_provider,
    email_verified = EXCLUDED.email_verified,
    phone_verified = EXCLUDED.phone_verified,
    status = EXCLUDED.status,
    updated_at = NOW();

INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, revoked, created_at)
VALUES (1001, (SELECT id FROM auth_schema.auth_accounts WHERE user_id = 1001), 'seed-refresh-token-customer-1001',
        NOW() + INTERVAL '30 days', FALSE, NOW() - INTERVAL '2 days'),
       (1002, (SELECT id FROM auth_schema.auth_accounts WHERE user_id = 1004), 'seed-refresh-token-admin-1004',
        NOW() + INTERVAL '30 days', FALSE, NOW() - INTERVAL '1 day') ON CONFLICT (id) DO
UPDATE SET
    account_id = EXCLUDED.account_id,
    token_hash = EXCLUDED.token_hash,
    expires_at = EXCLUDED.expires_at,
    revoked = EXCLUDED.revoked;

INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, used, created_at)
VALUES (1001, 'customer.seed@laundry.test', '$2a$10$iYGwq/z2LBYxTd92vFkTieFp8ZUrE.PHapoOqFETmA6tAAZCOXyfS',
        'EMAIL_LOGIN', NOW() + INTERVAL '10 minutes', FALSE, NOW()),
       (1002, 'admin.seed@laundry.test', '$2a$10$iYGwq/z2LBYxTd92vFkTieFp8ZUrE.PHapoOqFETmA6tAAZCOXyfS', 'ADMIN_2FA',
        NOW() + INTERVAL '10 minutes', FALSE, NOW()) ON CONFLICT (id) DO
UPDATE SET
    email = EXCLUDED.email,
    otp_hash = EXCLUDED.otp_hash,
    purpose = EXCLUDED.purpose,
    expires_at = EXCLUDED.expires_at,
    used = EXCLUDED.used;

SELECT setval(pg_get_serial_sequence('auth_schema.auth_accounts', 'id'),
              GREATEST((SELECT MAX(id) FROM auth_schema.auth_accounts), 1), true);
SELECT setval(pg_get_serial_sequence('auth_schema.refresh_tokens', 'id'),
              GREATEST((SELECT MAX(id) FROM auth_schema.refresh_tokens), 1), true);
SELECT setval(pg_get_serial_sequence('auth_schema.email_otps', 'id'),
              GREATEST((SELECT MAX(id) FROM auth_schema.email_otps), 1), true);

\echo
'Seeding store_db'
\connect store_db

INSERT INTO store_schema.stores (
    id, partner_id, name, contact_phone, address, latitude, longitude, image, description, is_active, status, created_at, updated_at
) VALUES
    (1001, 1001, 'District 1 Seed Store', '02871001001', '12 Nguyen Hue, District 1, Ho Chi Minh City', 10.7731, 106.7032, 'https://images.unsplash.com/photo-1604335399105-a0c585fd81a1', 'Flagship demo store near the city center', TRUE, 'ACTIVE', NOW() - INTERVAL '15 days', NOW()),
    (1002, 1001, 'Binh Thanh Seed Store', '02871001002', '45 Dien Bien Phu, Binh Thanh, Ho Chi Minh City', 10.8014, 106.7100, 'https://images.unsplash.com/photo-1626806787461-102c1bfaaea1', 'Residential demo laundry pickup point', TRUE, 'ACTIVE', NOW() - INTERVAL '14 days', NOW()),
    (1003, 1002, 'Thu Duc Pending Store', '02871001003', '88 Vo Van Ngan, Thu Duc, Ho Chi Minh City', 10.8499, 106.7718, 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64', 'Pending partner demo store', FALSE, 'PENDING', NOW() - INTERVAL '5 days', NOW())
ON CONFLICT (id) DO
UPDATE SET
    partner_id = EXCLUDED.partner_id,
    name = EXCLUDED.name,
    contact_phone = EXCLUDED.contact_phone,
    address = EXCLUDED.address,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude,
    image = EXCLUDED.image,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active,
    status = EXCLUDED.status,
    updated_at = NOW();

SELECT setval(pg_get_serial_sequence('store_schema.stores', 'id'),
              GREATEST((SELECT MAX(id) FROM store_schema.stores), 1), true);

\echo
'Seeding locker_db'
\connect locker_db

INSERT INTO locker_schema.lockers (
    id, store_id, code, name, status, address, latitude, longitude, description, created_at, updated_at
) VALUES
    (1001, 1001, 'LCK-D1-001', 'District 1 Locker 001', 'ACTIVE', '12 Nguyen Hue, District 1, Ho Chi Minh City', 10.7731, 106.7032, 'Main outdoor locker for demo orders', NOW() - INTERVAL '14 days', NOW()),
    (1002, 1002, 'LCK-BT-001', 'Binh Thanh Locker 001', 'ACTIVE', '45 Dien Bien Phu, Binh Thanh, Ho Chi Minh City', 10.8014, 106.7100, 'Residential area locker', NOW() - INTERVAL '13 days', NOW()),
    (1003, 1001, 'LCK-D1-MAINT', 'District 1 Maintenance Locker', 'MAINTENANCE', '12 Nguyen Hue, District 1, Ho Chi Minh City', 10.7730, 106.7030, 'Locker under scheduled maintenance', NOW() - INTERVAL '10 days', NOW())
ON CONFLICT (id) DO
UPDATE SET
    store_id = EXCLUDED.store_id,
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    status = EXCLUDED.status,
    address = EXCLUDED.address,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude,
    description = EXCLUDED.description,
    updated_at = NOW();

INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, description, status, created_at,
                                        updated_at)
VALUES (1001, 1001, 1, 'SMALL', TRUE, 'Small box for light laundry', 'AVAILABLE', NOW() - INTERVAL '14 days', NOW()),
       (1002, 1001, 2, 'MEDIUM', TRUE, 'Medium box reserved for active order', 'RESERVED', NOW() - INTERVAL '14 days',
        NOW()),
       (1003, 1001, 3, 'LARGE', TRUE, 'Large box currently occupied', 'OCCUPIED', NOW() - INTERVAL '14 days', NOW()),
       (1004, 1002, 1, 'MEDIUM', TRUE, 'Binh Thanh available box', 'AVAILABLE', NOW() - INTERVAL '13 days', NOW()),
       (1005, 1002, 2, 'LARGE', TRUE, 'Binh Thanh occupied box', 'OCCUPIED', NOW() - INTERVAL '13 days', NOW()),
       (1006, 1003, 1, 'MEDIUM', FALSE, 'Maintenance box disabled', 'MAINTENANCE', NOW() - INTERVAL '10 days',
        NOW()) ON CONFLICT (id) DO
UPDATE SET
    locker_id = EXCLUDED.locker_id,
    box_number = EXCLUDED.box_number,
    size = EXCLUDED.size,
    is_active = EXCLUDED.is_active,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    updated_at = NOW();

INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, resolved_by_user_id,
                                          resolved_at, created_at, updated_at)
VALUES (1001, 1003, 1002, 'Door sensor warning', 'Maintenance locker reports intermittent door sensor signal', 'OPEN',
        NULL, NULL, NOW() - INTERVAL '3 days', NOW()),
       (1002, 1001, 1001, 'Screen brightness issue', 'Customer reported low display brightness at night', 'RESOLVED',
        1002, NOW() - INTERVAL '1 day', NOW() - INTERVAL '2 days', NOW()) ON CONFLICT (id) DO
UPDATE SET
    locker_id = EXCLUDED.locker_id,
    user_id = EXCLUDED.user_id,
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    resolved_by_user_id = EXCLUDED.resolved_by_user_id,
    resolved_at = EXCLUDED.resolved_at,
    updated_at = NOW();

SELECT setval(pg_get_serial_sequence('locker_schema.lockers', 'id'),
              GREATEST((SELECT MAX(id) FROM locker_schema.lockers), 1), true);
SELECT setval(pg_get_serial_sequence('locker_schema.locker_boxes', 'id'),
              GREATEST((SELECT MAX(id) FROM locker_schema.locker_boxes), 1), true);
SELECT setval(pg_get_serial_sequence('locker_schema.locker_reports', 'id'),
              GREATEST((SELECT MAX(id) FROM locker_schema.locker_reports), 1), true);

\echo
'Seeding laundry_db'
\connect laundry_db

INSERT INTO laundry_schema.laundry_catalog_items (
    id, store_id, name, category, service_type, unit_price, max_price, unit, description, image, is_addon, is_monthly_package, estimated_hours, status, created_at, updated_at
) VALUES
    (1001, 1001, 'Wash and Fold', 'LAUNDRY', 'WASH_FOLD', 35000, 250000, 'kg', 'Standard wash, dry, and fold service', 'https://images.unsplash.com/photo-1517677208171-0bc6725a3e60', FALSE, FALSE, 24, 'ACTIVE', NOW() - INTERVAL '14 days', NOW()),
    (1002, 1001, 'Dry Cleaning Shirt', 'DRY_CLEANING', 'DRY_CLEAN', 60000, NULL, 'item', 'Dry cleaning for shirts and office wear', 'https://images.unsplash.com/photo-1598032895397-b9472444bf93', FALSE, FALSE, 48, 'ACTIVE', NOW() - INTERVAL '14 days', NOW()),
    (1003, 1001, 'Express 6 Hours', 'LAUNDRY', 'EXPRESS', 85000, 350000, 'kg', 'Fast turnaround express service', 'https://images.unsplash.com/photo-1582735689369-4fe89db7114c', TRUE, FALSE, 6, 'ACTIVE', NOW() - INTERVAL '12 days', NOW()),
    (1004, 1002, 'Blanket Cleaning', 'HOUSEHOLD', 'WASH', 120000, NULL, 'item', 'Large blanket and bedding cleaning', 'https://images.unsplash.com/photo-1585421514738-01798e348b17', FALSE, FALSE, 72, 'ACTIVE', NOW() - INTERVAL '11 days', NOW()),
    (1005, 1002, 'Monthly Laundry Plan', 'PACKAGE', 'MONTHLY', 499000, NULL, 'month', 'Monthly package for recurring customers', 'https://images.unsplash.com/photo-1521656693074-0ef32e80a5d5', FALSE, TRUE, 24, 'ACTIVE', NOW() - INTERVAL '10 days', NOW())
ON CONFLICT (id) DO
UPDATE SET
    store_id = EXCLUDED.store_id,
    name = EXCLUDED.name,
    category = EXCLUDED.category,
    service_type = EXCLUDED.service_type,
    unit_price = EXCLUDED.unit_price,
    max_price = EXCLUDED.max_price,
    unit = EXCLUDED.unit,
    description = EXCLUDED.description,
    image = EXCLUDED.image,
    is_addon = EXCLUDED.is_addon,
    is_monthly_package = EXCLUDED.is_monthly_package,
    estimated_hours = EXCLUDED.estimated_hours,
    status = EXCLUDED.status,
    updated_at = NOW();

SELECT setval(pg_get_serial_sequence('laundry_schema.laundry_catalog_items', 'id'),
              GREATEST((SELECT MAX(id) FROM laundry_schema.laundry_catalog_items), 1), true);

\echo
'Seeding order_db'
\connect order_db

INSERT INTO order_schema.promotions (
    id, code, name, discount_type, discount_value, max_discount_amount, min_order_amount, stackable, status, start_at, end_at, usage_count, created_by_user_id, created_at, updated_at
) VALUES
    (1001, 'WELCOME20', 'Welcome 20 Percent Off', 'PERCENTAGE', 20, 50000, 100000, FALSE, 'ACTIVE', NOW() - INTERVAL '30 days', NOW() + INTERVAL '60 days', 12, 1004, NOW() - INTERVAL '30 days', NOW()),
    (1002, 'FREESHIP', 'Free Shipping Voucher', 'FIXED_AMOUNT', 25000, 25000, 80000, TRUE, 'ACTIVE', NOW() - INTERVAL '20 days', NOW() + INTERVAL '40 days', 8, 1004, NOW() - INTERVAL '20 days', NOW()),
    (1003, 'VIP50K', 'VIP Customer Discount', 'FIXED_AMOUNT', 50000, 50000, 200000, FALSE, 'INACTIVE', NOW() - INTERVAL '60 days', NOW() - INTERVAL '1 day', 4, 1004, NOW() - INTERVAL '60 days', NOW())
ON CONFLICT (id) DO
UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    discount_type = EXCLUDED.discount_type,
    discount_value = EXCLUDED.discount_value,
    max_discount_amount = EXCLUDED.max_discount_amount,
    min_order_amount = EXCLUDED.min_order_amount,
    stackable = EXCLUDED.stackable,
    status = EXCLUDED.status,
    start_at = EXCLUDED.start_at,
    end_at = EXCLUDED.end_at,
    usage_count = EXCLUDED.usage_count,
    created_by_user_id = EXCLUDED.created_by_user_id,
    updated_at = NOW();

INSERT INTO order_schema.orders (id, order_code, user_id, receiver_id, receiver_phone, receiver_name, locker_id,
                                 send_box_id, receive_box_id, store_id, staff_id, type, service_category, status,
                                 actual_weight, weight_unit, extra_fee, discount, reservation_fee, storage_price,
                                 shipping_fee, total_price, original_price, promotion_code, applied_promotion_codes,
                                 pin_code, pin_code_issued_at, receive_at, intended_receive_at, completed_at,
                                 returned_at, pickup_deadline, description, customer_note, staff_note, cancel_reason,
                                 delivery_address, created_at, updated_at)
VALUES (1001, 'ORD-SEED-1001', 1001, 1001, '0901001001', 'Demo Customer', 1001, 1002, 1003, 1001, 1002, 'LAUNDRY',
        'LAUNDRY', 'PROCESSING', 3.50, 'kg', 10000, 50000, 15000, 0, 25000, 122500, 172500, 'WELCOME20',
        'WELCOME20,FREESHIP', '482913', NOW() - INTERVAL '8 days', NULL, NOW() + INTERVAL '1 day', NULL, NULL,
        NOW() + INTERVAL '3 days', 'Wash and fold order with express addon', 'Please handle office shirts carefully',
        'Items received and sorted', NULL, '12 Nguyen Hue, District 1', NOW() - INTERVAL '8 days', NOW()),
       (1002, 'ORD-SEED-1002', 1005, 1005, '0901001005', 'Vip Customer', 1002, 1005, NULL, 1002, 1002, 'LAUNDRY',
        'HOUSEHOLD', 'COMPLETED', 1.00, 'item', 0, 0, 0, 0, 0, 120000, 120000, NULL, NULL, '771204',
        NOW() - INTERVAL '7 days', NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days', NOW() - INTERVAL '3 days',
        NULL, NOW() - INTERVAL '2 days', 'Blanket cleaning completed', 'Call before delivery', 'Delivered to customer',
        NULL, '45 Dien Bien Phu, Binh Thanh', NOW() - INTERVAL '7 days', NOW()),
       (1003, 'ORD-SEED-1003', 1001, 1001, '0901001001', 'Demo Customer', 1001, 1001, NULL, 1001, NULL, 'LAUNDRY',
        'DRY_CLEANING', 'INITIALIZED', NULL, 'kg', 0, 0, 15000, 0, 0, 60000, 60000, NULL, NULL, '193847', NOW(), NULL,
        NOW() + INTERVAL '2 days', NULL, NULL, NOW() + INTERVAL '4 days', 'Dry cleaning shirt demo order',
        'No starch please', NULL, NULL, NULL, NOW(), NOW()),
       (1004, 'ORD-SEED-1004', 1005, 1005, '0901001005', 'Vip Customer', 1003, 1006, NULL, 1003, NULL, 'LAUNDRY',
        'PACKAGE', 'CANCELED', NULL, 'month', 0, 0, 0, 0, 0, 499000, 499000, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
        NULL, NULL, 'Monthly plan canceled before processing', 'Changed schedule', 'Canceled by customer request', 1,
        NULL, NOW() - INTERVAL '2 days', NOW()) ON CONFLICT (id) DO
UPDATE SET
    order_code = EXCLUDED.order_code,
    user_id = EXCLUDED.user_id,
    receiver_id = EXCLUDED.receiver_id,
    receiver_phone = EXCLUDED.receiver_phone,
    receiver_name = EXCLUDED.receiver_name,
    locker_id = EXCLUDED.locker_id,
    send_box_id = EXCLUDED.send_box_id,
    receive_box_id = EXCLUDED.receive_box_id,
    store_id = EXCLUDED.store_id,
    staff_id = EXCLUDED.staff_id,
    type = EXCLUDED.type,
    service_category = EXCLUDED.service_category,
    status = EXCLUDED.status,
    actual_weight = EXCLUDED.actual_weight,
    total_price = EXCLUDED.total_price,
    original_price = EXCLUDED.original_price,
    promotion_code = EXCLUDED.promotion_code,
    applied_promotion_codes = EXCLUDED.applied_promotion_codes,
    customer_note = EXCLUDED.customer_note,
    staff_note = EXCLUDED.staff_note,
    updated_at = NOW();

INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price, description)
VALUES (1001, 1001, 1001, 3.50, 122500, '3.5 kg wash and fold'),
       (1002, 1001, 1003, 1.00, 85000, 'Express addon'),
       (1003, 1002, 1004, 1.00, 120000, 'One blanket cleaning item'),
       (1004, 1003, 1002, 2.00, 120000, 'Two dry cleaning shirts'),
       (1005, 1004, 1005, 1.00, 499000, 'Monthly package request') ON CONFLICT (id) DO
UPDATE SET
    order_id = EXCLUDED.order_id,
    service_id = EXCLUDED.service_id,
    quantity = EXCLUDED.quantity,
    price = EXCLUDED.price,
    description = EXCLUDED.description;

INSERT INTO order_schema.order_status_history (id, order_id, old_status, new_status, changed_by_user_id, note,
                                               created_at)
VALUES (1001, 1001, NULL, 'INITIALIZED', 1001, 'Order created by customer', NOW() - INTERVAL '8 days'),
       (1002, 1001, 'INITIALIZED', 'PROCESSING', 1002, 'Staff collected items from locker', NOW() - INTERVAL '7 days'),
       (1003, 1002, NULL, 'INITIALIZED', 1005, 'VIP order created', NOW() - INTERVAL '7 days'),
       (1004, 1002, 'PROCESSING', 'COMPLETED', 1002, 'Order completed and delivered', NOW() - INTERVAL '3 days'),
       (1005, 1003, NULL, 'INITIALIZED', 1001, 'Dry cleaning order initialized', NOW()),
       (1006, 1004, 'INITIALIZED', 'CANCELED', 1005, 'Customer canceled monthly plan',
        NOW() - INTERVAL '2 days') ON CONFLICT (id) DO
UPDATE SET
    order_id = EXCLUDED.order_id,
    old_status = EXCLUDED.old_status,
    new_status = EXCLUDED.new_status,
    changed_by_user_id = EXCLUDED.changed_by_user_id,
    note = EXCLUDED.note;

INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, comment, created_at)
VALUES (1001, 1002, 1005, 5, 'Fast and clean service', NOW() - INTERVAL '2 days'),
       (1002, 1001, 1001, 4, 'Good service, waiting for pickup notification',
        NOW() - INTERVAL '1 day') ON CONFLICT (id) DO
UPDATE SET
    order_id = EXCLUDED.order_id,
    user_id = EXCLUDED.user_id,
    rating = EXCLUDED.rating,
    comment = EXCLUDED.comment;

INSERT INTO order_schema.order_complaints (id, order_id, user_id, type, description, status, created_at)
VALUES (1001, 1001, 1001, 'DELAY', 'Customer asks about pickup timing', 'OPEN', NOW() - INTERVAL '1 day'),
       (1002, 1002, 1005, 'OTHER', 'Customer requested invoice details', 'RESOLVED',
        NOW() - INTERVAL '2 days') ON CONFLICT (id) DO
UPDATE SET
    order_id = EXCLUDED.order_id,
    user_id = EXCLUDED.user_id,
    type = EXCLUDED.type,
    description = EXCLUDED.description,
    status = EXCLUDED.status;

SELECT setval(pg_get_serial_sequence('order_schema.orders', 'id'),
              GREATEST((SELECT MAX(id) FROM order_schema.orders), 1), true);
SELECT setval(pg_get_serial_sequence('order_schema.order_details', 'id'),
              GREATEST((SELECT MAX(id) FROM order_schema.order_details), 1), true);
SELECT setval(pg_get_serial_sequence('order_schema.order_status_history', 'id'),
              GREATEST((SELECT MAX(id) FROM order_schema.order_status_history), 1), true);
SELECT setval(pg_get_serial_sequence('order_schema.order_ratings', 'id'),
              GREATEST((SELECT MAX(id) FROM order_schema.order_ratings), 1), true);
SELECT setval(pg_get_serial_sequence('order_schema.order_complaints', 'id'),
              GREATEST((SELECT MAX(id) FROM order_schema.order_complaints), 1), true);
SELECT setval(pg_get_serial_sequence('order_schema.promotions', 'id'),
              GREATEST((SELECT MAX(id) FROM order_schema.promotions), 1), true);

\echo
'Seeding payment_db'
\connect payment_db

INSERT INTO payment_schema.payments (
    id, order_id, user_id, amount, method, status, reference_id, reference_transaction_id, content, qr, url, deeplink, description, created_at, updated_at
) VALUES
    (1001, 1001, 1001, 122500, 'VNPAY', 'COMPLETED', 'PAY-SEED-1001', 'VNPAY-TXN-1001', 'Payment for ORD-SEED-1001', 'qr-seed-1001', 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?seed=1001', NULL, 'Completed VNPay sample payment', NOW() - INTERVAL '8 days', NOW()),
    (1002, 1002, 1005, 120000, 'CASH', 'COMPLETED', 'PAY-SEED-1002', 'CASH-1002', 'Cash payment for ORD-SEED-1002', NULL, NULL, NULL, 'Paid directly at store', NOW() - INTERVAL '7 days', NOW()),
    (1003, 1003, 1001, 60000, 'MOMO', 'PENDING', 'PAY-SEED-1003', NULL, 'Pending MoMo payment for ORD-SEED-1003', 'qr-seed-1003', 'https://test-payment.momo.vn/seed/1003', 'momo://seed/1003', 'Pending MoMo sample payment', NOW(), NOW()),
    (1004, 1004, 1005, 499000, 'VNPAY', 'REFUNDED', 'PAY-SEED-1004', 'VNPAY-TXN-1004', 'Refunded payment for canceled order', NULL, NULL, NULL, 'Refunded canceled monthly plan', NOW() - INTERVAL '2 days', NOW())
ON CONFLICT (id) DO
UPDATE SET
    order_id = EXCLUDED.order_id,
    user_id = EXCLUDED.user_id,
    amount = EXCLUDED.amount,
    method = EXCLUDED.method,
    status = EXCLUDED.status,
    reference_id = EXCLUDED.reference_id,
    reference_transaction_id = EXCLUDED.reference_transaction_id,
    content = EXCLUDED.content,
    qr = EXCLUDED.qr,
    url = EXCLUDED.url,
    deeplink = EXCLUDED.deeplink,
    description = EXCLUDED.description,
    updated_at = NOW();

INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, status, reason, transaction_id,
                                    processed_by_user_id, requested_at, processed_at)
VALUES (1001, 1004, 1004, 499000, 'COMPLETED', 'Order canceled before processing', 'REF-SEED-1004', 1004,
        NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day') ON CONFLICT (id) DO
UPDATE SET
    payment_id = EXCLUDED.payment_id,
    order_id = EXCLUDED.order_id,
    amount = EXCLUDED.amount,
    status = EXCLUDED.status,
    reason = EXCLUDED.reason,
    transaction_id = EXCLUDED.transaction_id,
    processed_by_user_id = EXCLUDED.processed_by_user_id,
    processed_at = EXCLUDED.processed_at;

SELECT setval(pg_get_serial_sequence('payment_schema.payments', 'id'),
              GREATEST((SELECT MAX(id) FROM payment_schema.payments), 1), true);
SELECT setval(pg_get_serial_sequence('payment_schema.refunds', 'id'),
              GREATEST((SELECT MAX(id) FROM payment_schema.refunds), 1), true);

\echo
'Seeding notification_db'
\connect notification_db

INSERT INTO notification_schema.notifications (
    id, user_id, title, message, type, reference_id, reference_type, status, is_read, read_at, created_at
) VALUES
    (1001, 1001, 'Order received', 'Your order ORD-SEED-1001 has been received by the store.', 'ORDER', 1001, 'ORDER', 'READ', TRUE, NOW() - INTERVAL '7 days', NOW() - INTERVAL '8 days'),
    (1002, 1001, 'Payment completed', 'Payment PAY-SEED-1001 has been completed.', 'PAYMENT', 1001, 'PAYMENT', 'UNREAD', FALSE, NULL, NOW() - INTERVAL '8 days'),
    (1003, 1005, 'Order completed', 'Your blanket cleaning order ORD-SEED-1002 is complete.', 'ORDER', 1002, 'ORDER', 'READ', TRUE, NOW() - INTERVAL '2 days', NOW() - INTERVAL '3 days'),
    (1004, 1002, 'New assignment', 'You have been assigned to order ORD-SEED-1001.', 'STAFF', 1001, 'ORDER', 'UNREAD', FALSE, NULL, NOW() - INTERVAL '7 days'),
    (1005, 1004, 'Refund approved', 'Sample refund REF-SEED-1004 was processed.', 'PAYMENT', 1001, 'REFUND', 'UNREAD', FALSE, NULL, NOW() - INTERVAL '1 day')
ON CONFLICT (id) DO
UPDATE SET
    user_id = EXCLUDED.user_id,
    title = EXCLUDED.title,
    message = EXCLUDED.message,
    type = EXCLUDED.type,
    reference_id = EXCLUDED.reference_id,
    reference_type = EXCLUDED.reference_type,
    status = EXCLUDED.status,
    is_read = EXCLUDED.is_read,
    read_at = EXCLUDED.read_at;

INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at, updated_at)
VALUES (1001, 1001, 'seed-fcm-token-customer-android', 'ANDROID', NOW() - INTERVAL '8 days', NOW()),
       (1002, 1005, 'seed-fcm-token-vip-ios', 'IOS', NOW() - INTERVAL '7 days', NOW()),
       (1003, 1002, 'seed-fcm-token-staff-web', 'WEB', NOW() - INTERVAL '6 days', NOW()) ON CONFLICT (id) DO
UPDATE SET
    user_id = EXCLUDED.user_id,
    token = EXCLUDED.token,
    device_type = EXCLUDED.device_type,
    updated_at = NOW();

SELECT setval(pg_get_serial_sequence('notification_schema.notifications', 'id'),
              GREATEST((SELECT MAX(id) FROM notification_schema.notifications), 1), true);
SELECT setval(pg_get_serial_sequence('notification_schema.fcm_tokens', 'id'),
              GREATEST((SELECT MAX(id) FROM notification_schema.fcm_tokens), 1), true);

\echo
'Seeding iot_db'
\connect iot_db

INSERT INTO iot_schema.device_statuses (
    id, device_id, locker_id, status, last_seen_at, created_at, updated_at
) VALUES
    (1001, 'IOT-LCK-D1-001', 1001, 'ONLINE', NOW() - INTERVAL '2 minutes', NOW() - INTERVAL '14 days', NOW()),
    (1002, 'IOT-LCK-BT-001', 1002, 'ONLINE', NOW() - INTERVAL '5 minutes', NOW() - INTERVAL '13 days', NOW()),
    (1003, 'IOT-LCK-D1-MAINT', 1003, 'MAINTENANCE', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '10 days', NOW())
ON CONFLICT (id) DO
UPDATE SET
    device_id = EXCLUDED.device_id,
    locker_id = EXCLUDED.locker_id,
    status = EXCLUDED.status,
    last_seen_at = EXCLUDED.last_seen_at,
    updated_at = NOW();

SELECT setval(pg_get_serial_sequence('iot_schema.device_statuses', 'id'),
              GREATEST((SELECT MAX(id) FROM iot_schema.device_statuses), 1), true);

\echo
'Seeding staff_db'
\connect staff_db

INSERT INTO staff_schema.staff_assignments (
    id, staff_id, order_id, locker_id, status, created_at, updated_at
) VALUES
    (1001, 1002, 1001, 1001, 'ASSIGNED', NOW() - INTERVAL '8 days', NOW()),
    (1002, 1002, 1002, 1002, 'COMPLETED', NOW() - INTERVAL '7 days', NOW()),
    (1003, 1002, 1003, 1001, 'ASSIGNED', NOW(), NOW())
ON CONFLICT (id) DO
UPDATE SET
    staff_id = EXCLUDED.staff_id,
    order_id = EXCLUDED.order_id,
    locker_id = EXCLUDED.locker_id,
    status = EXCLUDED.status,
    updated_at = NOW();

SELECT setval(pg_get_serial_sequence('staff_schema.staff_assignments', 'id'),
              GREATEST((SELECT MAX(id) FROM staff_schema.staff_assignments), 1), true);

\echo
'Seeding loyalty_db'
\connect loyalty_db

INSERT INTO loyalty_schema.loyalty_accounts (
    id, user_id, points, stamps, tier, created_at, updated_at
) VALUES
    (1001, 1001, 320, 4, 'SILVER', NOW() - INTERVAL '20 days', NOW()),
    (1002, 1005, 980, 9, 'GOLD', NOW() - INTERVAL '12 days', NOW()),
    (1003, 1002, 120, 1, 'BRONZE', NOW() - INTERVAL '18 days', NOW())
ON CONFLICT (id) DO
UPDATE SET
    user_id = EXCLUDED.user_id,
    points = EXCLUDED.points,
    stamps = EXCLUDED.stamps,
    tier = EXCLUDED.tier,
    updated_at = NOW();

INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (1001, 1001, 1001, 120, 'EARN', NOW() - INTERVAL '8 days'),
       (1002, 1005, 1002, 150, 'EARN', NOW() - INTERVAL '3 days'),
       (1003, 1005, 1004, -50, 'REFUND_ADJUSTMENT', NOW() - INTERVAL '1 day'),
       (1004, 1001, NULL, 200, 'WELCOME_BONUS', NOW() - INTERVAL '20 days') ON CONFLICT (id) DO
UPDATE SET
    user_id = EXCLUDED.user_id,
    order_id = EXCLUDED.order_id,
    points = EXCLUDED.points,
    type = EXCLUDED.type;

SELECT setval(pg_get_serial_sequence('loyalty_schema.loyalty_accounts', 'id'),
              GREATEST((SELECT MAX(id) FROM loyalty_schema.loyalty_accounts), 1), true);
SELECT setval(pg_get_serial_sequence('loyalty_schema.point_transactions', 'id'),
              GREATEST((SELECT MAX(id) FROM loyalty_schema.point_transactions), 1), true);

\echo
'Demo data seed completed'
