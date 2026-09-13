\set
ON_ERROR_STOP on

\echo 'Seeding user_db'
\connect user_db

INSERT INTO user_schema.user_profiles (
    id, email, phone_number, first_name, last_name, birthday, image_url, status, roles, created_at, updated_at
) VALUES
    (1001, 'khachhang.chinh@laundry.vn', '0901001001', 'Nguyễn', 'Khách', DATE '1999-01-15', 'https://ui-avatars.com/api/?name=Nguyen+Khach', 'ACTIVE', 'USER', NOW() - INTERVAL '20 days', NOW()),
    (1002, 'nhanvien.hoatdong@laundry.vn', '0901001002', 'Trần', 'Nhân', DATE '1997-04-10', 'https://ui-avatars.com/api/?name=Tran+Nhan', 'ACTIVE', 'STAFF', NOW() - INTERVAL '18 days', NOW()),
    (1004, 'quantri.hethong@laundry.vn', '0901001004', 'Lê', 'Quản', DATE '1990-12-02', 'https://ui-avatars.com/api/?name=Le+Quan', 'ACTIVE', 'ADMIN', NOW() - INTERVAL '16 days', NOW()),
    (1005, 'khachhang.vip@laundry.vn', '0901001005', 'Phạm', 'Thành', DATE '1995-06-30', 'https://ui-avatars.com/api/?name=Pham+Thanh', 'ACTIVE', 'USER', NOW() - INTERVAL '12 days', NOW())
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

SELECT setval(pg_get_serial_sequence('user_schema.user_profiles', 'id'),
              GREATEST((SELECT MAX(id) FROM user_schema.user_profiles), 1), true);

\echo
'Seeding auth_db'
\connect auth_db

INSERT INTO auth_schema.auth_accounts (
    user_id, email, phone_number, password_hash, auth_provider, email_verified, phone_verified, status, last_login_at, created_at, updated_at
) VALUES
    (1001, 'khachhang.chinh@laundry.vn', '0901001001', '$2a$10$iYGwq/z2LBYxTd92vFkTieFp8ZUrE.PHapoOqFETmA6tAAZCOXyfS', 'LOCAL', TRUE, TRUE, 'ACTIVE', NOW() - INTERVAL '2 days', NOW() - INTERVAL '20 days', NOW()),
    (1002, 'nhanvien.hoatdong@laundry.vn', '0901001002', '$2a$10$iYGwq/z2LBYxTd92vFkTieFp8ZUrE.PHapoOqFETmA6tAAZCOXyfS', 'LOCAL', TRUE, TRUE, 'ACTIVE', NOW() - INTERVAL '3 days', NOW() - INTERVAL '18 days', NOW()),
    (1004, 'quantri.hethong@laundry.vn', '0901001004', '$2a$10$iYGwq/z2LBYxTd92vFkTieFp8ZUrE.PHapoOqFETmA6tAAZCOXyfS', 'LOCAL', TRUE, TRUE, 'ACTIVE', NOW() - INTERVAL '1 day', NOW() - INTERVAL '16 days', NOW()),
    (1005, 'khachhang.vip@laundry.vn', '0901001005', '$2a$10$iYGwq/z2LBYxTd92vFkTieFp8ZUrE.PHapoOqFETmA6tAAZCOXyfS', 'LOCAL', TRUE, TRUE, 'ACTIVE', NULL, NOW() - INTERVAL '12 days', NOW())
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
VALUES (1001, 'khachhang.chinh@laundry.vn', '$2a$10$iYGwq/z2LBYxTd92vFkTieFp8ZUrE.PHapoOqFETmA6tAAZCOXyfS',
        'EMAIL_LOGIN', NOW() + INTERVAL '10 minutes', FALSE, NOW()),
       (1002, 'quantri.hethong@laundry.vn', '$2a$10$iYGwq/z2LBYxTd92vFkTieFp8ZUrE.PHapoOqFETmA6tAAZCOXyfS', 'ADMIN_2FA',
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
    id, name, contact_phone, address, latitude, longitude, image, description, is_active, status, created_at, updated_at
) VALUES
    (1001, 'Cửa hàng Giặt Sấy AISL Quận 1', '02871001001', '12 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh', 10.7731, 106.7032, 'https://images.unsplash.com/photo-1604335399105-a0c585fd81a1', 'Cửa hàng trung tâm với trang thiết bị hiện đại', TRUE, 'ACTIVE', NOW() - INTERVAL '15 days', NOW()),
    (1002, 'Cửa hàng Giặt Sấy AISL Bình Thạnh', '02871001002', '45 Điện Biên Phủ, Quận Bình Thạnh, TP. Hồ Chí Minh', 10.8014, 106.7100, 'https://images.unsplash.com/photo-1626806787461-102c1bfaaea1', 'Cửa hàng tiện lợi phục vụ khu vực đông dân cư', TRUE, 'ACTIVE', NOW() - INTERVAL '14 days', NOW()),
    (1003, 'Cửa hàng Giặt Sấy AISL Thủ Đức', '02871001003', '88 Võ Văn Ngân, TP. Thủ Đức, TP. Hồ Chí Minh', 10.8499, 106.7718, 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64', 'Cửa hàng đối tác đang trong quá trình xét duyệt', FALSE, 'PENDING', NOW() - INTERVAL '5 days', NOW())
ON CONFLICT (id) DO
UPDATE SET
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
    (1001, 1001, 'LCK-D1-001', 'Tủ Khóa AISL Quận 1 - 01', 'ACTIVE', '12 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh', 10.7731, 106.7032, 'Tủ khóa ngoài trời chính tại khu vực trung tâm', NOW() - INTERVAL '14 days', NOW()),
    (1002, 1002, 'LCK-BT-001', 'Tủ Khóa AISL Bình Thạnh - 01', 'ACTIVE', '45 Điện Biên Phủ, Quận Bình Thạnh, TP. Hồ Chí Minh', 10.8014, 106.7100, 'Tủ khóa thông minh tự phục vụ khu vực cư dân', NOW() - INTERVAL '13 days', NOW()),
    (1003, 1001, 'LCK-D1-MAINT', 'Tủ Khóa AISL Quận 1 - Bảo Trì', 'MAINTENANCE', '12 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh', 10.7730, 106.7030, 'Tủ khóa đang trong quá trình bảo trì định kỳ', NOW() - INTERVAL '10 days', NOW())
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
VALUES (1001, 1001, 1, 'SMALL', TRUE, 'Ô tủ nhỏ gọn', 'AVAILABLE', NOW() - INTERVAL '14 days', NOW()),
       (1002, 1001, 2, 'MEDIUM', TRUE, 'Ô tủ cỡ trung bình', 'RESERVED', NOW() - INTERVAL '14 days', NOW()),
       (1003, 1001, 3, 'LARGE', TRUE, 'Ô tủ lớn đang được sử dụng', 'OCCUPIED', NOW() - INTERVAL '14 days', NOW()),
       (1004, 1002, 1, 'MEDIUM', TRUE, 'Ô tủ trống', 'AVAILABLE', NOW() - INTERVAL '13 days', NOW()),
       (1005, 1002, 2, 'LARGE', TRUE, 'Ô tủ đang chứa đồ', 'OCCUPIED', NOW() - INTERVAL '13 days', NOW()),
       (1006, 1003, 1, 'MEDIUM', FALSE, 'Ô tủ đang tạm ngưng phục vụ', 'MAINTENANCE', NOW() - INTERVAL '10 days',
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
VALUES (1001, 1003, 1002, 'Cảnh báo cảm biến cửa', 'Tủ khóa bảo trì báo lỗi cảm biến mở cửa', 'OPEN', NULL, NULL,
        NOW() - INTERVAL '3 days', NOW()),
       (1002, 1001, 1001, 'Vấn đề độ sáng màn hình', 'Độ sáng màn hình giảm vào ban đêm', 'RESOLVED', 1002,
        NOW() - INTERVAL '1 day', NOW() - INTERVAL '2 days', NOW()) ON CONFLICT (id) DO
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
    (1001, 1001, 'Giặt Sấy Tiêu Chuẩn', 'LAUNDRY', 'WASH_FOLD', 35000, 250000, 'kg', 'Dịch vụ giặt, sấy và gấp gọn đồ tiêu chuẩn', 'https://images.unsplash.com/photo-1517677208171-0bc6725a3e60', FALSE, FALSE, 24, 'ACTIVE', NOW() - INTERVAL '14 days', NOW()),
    (1002, 1001, 'Giặt Hấp Áo Sơ Mi', 'DRY_CLEANING', 'DRY_CLEAN', 60000, NULL, 'item', 'Giặt hấp cao cấp dành cho áo sơ mi và đồ công sở', 'https://images.unsplash.com/photo-1598032895397-b9472444bf93', FALSE, FALSE, 48, 'ACTIVE', NOW() - INTERVAL '14 days', NOW()),
    (1003, 1001, 'Giặt Sấy Siêu Tốc 6 Giờ', 'LAUNDRY', 'EXPRESS', 85000, 350000, 'kg', 'Dịch vụ xử lý siêu tốc lấy ngay trong 6 tiếng', 'https://images.unsplash.com/photo-1582735689369-4fe89db7114c', TRUE, FALSE, 6, 'ACTIVE', NOW() - INTERVAL '12 days', NOW()),
    (1004, 1002, 'Giặt Chăn Ga Gối', 'HOUSEHOLD', 'WASH', 120000, NULL, 'item', 'Làm sạch sâu các loại chăn mền, ga trải giường cỡ lớn', 'https://images.unsplash.com/photo-1585421514738-01798e348b17', FALSE, FALSE, 72, 'ACTIVE', NOW() - INTERVAL '11 days', NOW()),
    (1005, 1002, 'Gói Giặt Sấy Tháng', 'PACKAGE', 'MONTHLY', 499000, NULL, 'month', 'Gói dịch vụ giặt sấy theo tháng với chi phí ưu đãi', 'https://images.unsplash.com/photo-1521656693074-0ef32e80a5d5', FALSE, TRUE, 24, 'ACTIVE', NOW() - INTERVAL '10 days', NOW())
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
    (1001, 'CHAO20', 'Giảm 20% Chào Bạn Mới', 'PERCENTAGE', 20, 50000, 100000, FALSE, 'ACTIVE', NOW() - INTERVAL '30 days', NOW() + INTERVAL '60 days', 12, 1004, NOW() - INTERVAL '30 days', NOW()),
    (1002, 'MIENSHIP', 'Miễn Phí Vận Chuyển', 'FIXED_AMOUNT', 25000, 25000, 80000, TRUE, 'ACTIVE', NOW() - INTERVAL '20 days', NOW() + INTERVAL '40 days', 8, 1004, NOW() - INTERVAL '20 days', NOW()),
    (1003, 'VIP50K', 'Ưu Đãi Khách Hàng VIP', 'FIXED_AMOUNT', 50000, 50000, 200000, FALSE, 'INACTIVE', NOW() - INTERVAL '60 days', NOW() - INTERVAL '1 day', 4, 1004, NOW() - INTERVAL '60 days', NOW())
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
VALUES (1001, 'ORD-SEED-1001', 1001, 1001, '0901001001', 'Nguyễn Khách', 1001, 1002, 1003, 1001, 1002, 'LAUNDRY',
        'LAUNDRY', 'PROCESSING', 3.50, 'kg', 10000, 50000, 15000, 0, 25000, 122500, 172500, 'CHAO20', 'CHAO20,MIENSHIP',
        '482913', NOW() - INTERVAL '8 days', NULL, NOW() + INTERVAL '1 day', NULL, NULL, NOW() + INTERVAL '3 days',
        'Đơn giặt sấy kèm dịch vụ siêu tốc', 'Xin hãy giặt cẩn thận áo sơ mi công sở',
        'Đã nhận đồ và bắt đầu phân loại', NULL, '12 Nguyễn Huệ, Quận 1', NOW() - INTERVAL '8 days', NOW()),
       (1002, 'ORD-SEED-1002', 1005, 1005, '0901001005', 'Phạm Thành', 1002, 1005, NULL, 1002, 1002, 'LAUNDRY',
        'HOUSEHOLD', 'COMPLETED', 1.00, 'item', 0, 0, 0, 0, 0, 120000, 120000, NULL, NULL, '771204',
        NOW() - INTERVAL '7 days', NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days', NOW() - INTERVAL '3 days',
        NULL, NOW() - INTERVAL '2 days', 'Đơn vệ sinh chăn mền lớn', 'Gọi trước khi giao hàng',
        'Đã giao hàng thành công', NULL, '45 Điện Biên Phủ, Quận Bình Thạnh', NOW() - INTERVAL '7 days', NOW()),
       (1003, 'ORD-SEED-1003', 1001, 1001, '0901001001', 'Nguyễn Khách', 1001, 1001, NULL, 1001, NULL, 'LAUNDRY',
        'DRY_CLEANING', 'INITIALIZED', NULL, 'kg', 0, 0, 15000, 0, 0, 60000, 60000, NULL, NULL, '193847', NOW(), NULL,
        NOW() + INTERVAL '2 days', NULL, NULL, NOW() + INTERVAL '4 days', 'Đơn giặt hấp áo sơ mi', 'Không ủi hồ nhé',
        NULL, NULL, NULL, NOW(), NOW()),
       (1004, 'ORD-SEED-1004', 1005, 1005, '0901001005', 'Phạm Thành', 1003, 1006, NULL, 1003, NULL, 'LAUNDRY',
        'PACKAGE', 'CANCELED', NULL, 'month', 0, 0, 0, 0, 0, 499000, 499000, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
        NULL, NULL, 'Gói dịch vụ tháng bị hủy trước khi bắt đầu', 'Tôi thay đổi lịch trình',
        'Hủy theo yêu cầu của khách hàng', 1, NULL, NOW() - INTERVAL '2 days', NOW()) ON CONFLICT (id) DO
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
VALUES (1001, 1001, 1001, 3.50, 122500, 'Giặt sấy tiêu chuẩn 3.5 kg'),
       (1002, 1001, 1003, 1.00, 85000, 'Dịch vụ cộng thêm siêu tốc'),
       (1003, 1002, 1004, 1.00, 120000, 'Vệ sinh một chiếc chăn lớn'),
       (1004, 1003, 1002, 2.00, 120000, 'Giặt hấp hai áo sơ mi'),
       (1005, 1004, 1005, 1.00, 499000, 'Đăng ký gói giặt sấy tháng') ON CONFLICT (id) DO
UPDATE SET
    order_id = EXCLUDED.order_id,
    service_id = EXCLUDED.service_id,
    quantity = EXCLUDED.quantity,
    price = EXCLUDED.price,
    description = EXCLUDED.description;

INSERT INTO order_schema.order_status_history (id, order_id, old_status, new_status, changed_by_user_id, note,
                                               created_at)
VALUES (1001, 1001, NULL, 'INITIALIZED', 1001, 'Khách hàng khởi tạo đơn hàng', NOW() - INTERVAL '8 days'),
       (1002, 1001, 'INITIALIZED', 'PROCESSING', 1002, 'Nhân viên đã lấy đồ từ tủ khóa', NOW() - INTERVAL '7 days'),
       (1003, 1002, NULL, 'INITIALIZED', 1005, 'Khởi tạo đơn hàng VIP', NOW() - INTERVAL '7 days'),
       (1004, 1002, 'PROCESSING', 'COMPLETED', 1002, 'Đơn hàng đã hoàn thành và giao thành công',
        NOW() - INTERVAL '3 days'),
       (1005, 1003, NULL, 'INITIALIZED', 1001, 'Đơn hàng giặt hấp mới', NOW()),
       (1006, 1004, 'INITIALIZED', 'CANCELED', 1005, 'Khách hàng hủy đăng ký gói',
        NOW() - INTERVAL '2 days') ON CONFLICT (id) DO
UPDATE SET
    order_id = EXCLUDED.order_id,
    old_status = EXCLUDED.old_status,
    new_status = EXCLUDED.new_status,
    changed_by_user_id = EXCLUDED.changed_by_user_id,
    note = EXCLUDED.note;

INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, comment, created_at)
VALUES (1001, 1002, 1005, 5, 'Dịch vụ nhanh chóng và quần áo rất sạch sẽ', NOW() - INTERVAL '2 days'),
       (1002, 1001, 1001, 4, 'Chất lượng tốt, nhưng đang chờ thông báo lấy đồ',
        NOW() - INTERVAL '1 day') ON CONFLICT (id) DO
UPDATE SET
    order_id = EXCLUDED.order_id,
    user_id = EXCLUDED.user_id,
    rating = EXCLUDED.rating,
    comment = EXCLUDED.comment;

INSERT INTO order_schema.order_complaints (id, order_id, user_id, type, description, status, created_at)
VALUES (1001, 1001, 1001, 'DELAY', 'Khách hàng hỏi thời gian nhận đồ', 'OPEN', NOW() - INTERVAL '1 day'),
       (1002, 1002, 1005, 'OTHER', 'Khách hàng yêu cầu xuất hóa đơn', 'RESOLVED',
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
    (1001, 1001, 1001, 122500, 'VNPAY', 'COMPLETED', 'PAY-SEED-1001', 'VNPAY-TXN-1001', 'Thanh toán đơn hàng ORD-SEED-1001', 'qr-seed-1001', 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?seed=1001', NULL, 'Thanh toán VNPay thành công', NOW() - INTERVAL '8 days', NOW()),
    (1002, 1002, 1005, 120000, 'CASH', 'COMPLETED', 'PAY-SEED-1002', 'CASH-1002', 'Thanh toán tiền mặt cho đơn ORD-SEED-1002', NULL, NULL, NULL, 'Thanh toán trực tiếp tại cửa hàng', NOW() - INTERVAL '7 days', NOW()),
    (1003, 1003, 1001, 60000, 'MOMO', 'PENDING', 'PAY-SEED-1003', NULL, 'Thanh toán MoMo đang chờ cho đơn ORD-SEED-1003', 'qr-seed-1003', 'https://test-payment.momo.vn/seed/1003', 'momo://seed/1003', 'Giao dịch MoMo đang chờ xử lý', NOW(), NOW()),
    (1004, 1004, 1005, 499000, 'VNPAY', 'REFUNDED', 'PAY-SEED-1004', 'VNPAY-TXN-1004', 'Hoàn tiền cho đơn hàng đã hủy', NULL, NULL, NULL, 'Hoàn tiền gói dịch vụ tháng đã hủy', NOW() - INTERVAL '2 days', NOW())
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
VALUES (1001, 1004, 1004, 499000, 'COMPLETED', 'Đơn hàng bị hủy trước khi bắt đầu', 'REF-SEED-1004', 1004,
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
    (1001, 1001, 'Đơn hàng đã được nhận', 'Đơn hàng ORD-SEED-1001 của bạn đã được nhận bởi cửa hàng.', 'ORDER', 1001, 'ORDER', 'READ', TRUE, NOW() - INTERVAL '7 days', NOW() - INTERVAL '8 days'),
    (1002, 1001, 'Thanh toán thành công', 'Giao dịch thanh toán PAY-SEED-1001 đã hoàn thành.', 'PAYMENT', 1001, 'PAYMENT', 'UNREAD', FALSE, NULL, NOW() - INTERVAL '8 days'),
    (1003, 1005, 'Hoàn thành đơn hàng', 'Đơn hàng giặt chăn mền ORD-SEED-1002 đã hoàn thành.', 'ORDER', 1002, 'ORDER', 'READ', TRUE, NOW() - INTERVAL '2 days', NOW() - INTERVAL '3 days'),
    (1004, 1002, 'Nhiệm vụ mới', 'Bạn được giao xử lý đơn hàng ORD-SEED-1001.', 'STAFF', 1001, 'ORDER', 'UNREAD', FALSE, NULL, NOW() - INTERVAL '7 days'),
    (1005, 1004, 'Hoàn tiền được duyệt', 'Giao dịch hoàn tiền REF-SEED-1004 đã được xử lý.', 'PAYMENT', 1001, 'REFUND', 'UNREAD', FALSE, NULL, NOW() - INTERVAL '1 day')
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
'Vietnamese real data seed completed'
