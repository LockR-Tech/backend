\set
ON_ERROR_STOP on

\echo 'Seeding user_db'
\connect user_db

INSERT INTO user_schema.user_profiles (
    id, email, phone_number, first_name, last_name, birthday, status, roles, created_at, updated_at
) VALUES
    (54, 'binhtntse182370@fpt.edu.vn', '0869371051', 'Bình', 'Trương Nguyễn Thái', DATE '2000-01-01', 'ACTIVE', 'USER', NOW(), NOW())
ON CONFLICT (id) DO
UPDATE SET
    email = EXCLUDED.email,
    phone_number = EXCLUDED.phone_number,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    roles = EXCLUDED.roles,
    status = EXCLUDED.status,
    updated_at = NOW();

\echo
'Seeding auth_db'
\connect auth_db

INSERT INTO auth_schema.auth_accounts (
    user_id, email, phone_number, password_hash, auth_provider, email_verified, phone_verified, status, last_login_at, created_at, updated_at
) VALUES
    (54, 'binhtntse182370@fpt.edu.vn', '0869371051', '$2a$10$iYGwq/z2LBYxTd92vFkTieFp8ZUrE.PHapoOqFETmA6tAAZCOXyfS', 'LOCAL', TRUE, TRUE, 'ACTIVE', NOW(), NOW(), NOW())
ON CONFLICT (user_id) DO
UPDATE SET
    email = EXCLUDED.email,
    password_hash = EXCLUDED.password_hash,
    status = EXCLUDED.status,
    updated_at = NOW();

\echo
'Seeding order_db'
\connect order_db

INSERT INTO order_schema.orders (
    id, order_code, user_id, receiver_id, receiver_phone, receiver_name, locker_id, send_box_id, receive_box_id, store_id, staff_id, type, service_category, status, actual_weight, weight_unit, extra_fee, discount, reservation_fee, storage_price, shipping_fee, total_price, original_price, pin_code, pin_code_issued_at, description, created_at, updated_at
) VALUES
    (1054, 'ORD-BINH-1054', 54, 54, '0869371051', 'Bình Trương', 1001, 1001, NULL, 1001, NULL, 'LAUNDRY', 'LAUNDRY', 'INITIALIZED', NULL, 'kg', 0, 0, 15000, 0, 0, 60000, 60000, '123456', NOW(), 'Binh test order', NOW(), NOW())
ON CONFLICT (id) DO
UPDATE SET status = EXCLUDED.status;

INSERT INTO order_schema.order_status_history (id, order_id, old_status, new_status, changed_by_user_id, note,
                                               created_at)
VALUES (1054, 1054, NULL, 'INITIALIZED', 54, 'Order created', NOW()) ON CONFLICT (id) DO NOTHING;

\echo
'Seeding loyalty_db'
\connect loyalty_db

INSERT INTO loyalty_schema.loyalty_accounts (
    id, user_id, points, stamps, tier, created_at, updated_at
) VALUES
    (54, 54, 580, 4, 'SILVER', NOW(), NOW())
ON CONFLICT (id) DO
UPDATE SET points = EXCLUDED.points, stamps = EXCLUDED.stamps;

\echo
'Done seeding Binh test user for microservices'
