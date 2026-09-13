import random
import uuid
import datetime

# --- DATA POOLS ---
FIRST_NAMES = ["Anh", "Bình", "Châu", "Dương", "Đạt", "Hoa", "Hùng", "Hải", "Khánh", "Linh", "Minh", "Ngọc", "Oanh", "Phong", "Quang", "Sang", "Trang", "Tuấn", "Uyên", "Vinh"]
LAST_NAMES = ["Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Võ", "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô", "Dương", "Lý"]
MIDDLE_NAMES = ["Thị", "Văn", "Hữu", "Thanh", "Minh", "Thu", "Ngọc", "Gia", "Bảo", "Tuấn"]

STREETS = ["Lê Lợi", "Nguyễn Huệ", "Trần Hưng Đạo", "Phạm Ngũ Lão", "Hàm Nghi", "Pasteur", "Lý Tự Trọng", "Hai Bà Trưng", "Nguyễn Đình Chiểu", "Lê Duẩn"]
CITIES = ["Hà Nội", "Hồ Chí Minh", "Đà Nẵng", "Cần Thơ", "Hải Phòng"]

def gen_name():
    return f"{random.choice(LAST_NAMES)} {random.choice(MIDDLE_NAMES)} {random.choice(FIRST_NAMES)}"

def gen_phone():
    return "09" + "".join([str(random.randint(0, 9)) for _ in range(8)])

def gen_address():
    return f"{random.randint(1, 999)} {random.choice(STREETS)}, Quận {random.randint(1, 12)}, {random.choice(CITIES)}"

def escape_sql(s):
    if s is None:
        return "NULL"
    if isinstance(s, str):
        return "'" + s.replace("'", "''") + "'"
    return str(s)

def main():
    output = []
    output.append("-- ==========================================")
    output.append("-- SEED 100 REALISTIC RECORDS PER TABLE")
    output.append("-- ==========================================\n")

    base_id = 10000
    count = 100
    now = datetime.datetime.now()

    # 1. user_db
    output.append("\\connect user_db")
    output.append("DELETE FROM user_schema.user_profiles WHERE id >= 10000;")
    users_sql = []
    for i in range(count):
        user_id = base_id + i
        name = gen_name()
        phone = gen_phone()
        email = f"user{user_id}@laundry.test"
        role = random.choice(["CUSTOMER", "CUSTOMER", "CUSTOMER", "MANAGER", "MAINTENANCE"])
        avatar = f"https://api.dicebear.com/7.x/avataaars/svg?seed={user_id}"
        first_name = name.split()[0] if " " in name else name
        last_name = name.split(" ", 1)[1] if " " in name else ""
        users_sql.append(f"INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status, created_at, updated_at) VALUES ({user_id}, '{phone}', '{email}', {escape_sql(first_name)}, {escape_sql(last_name)}, '{avatar}', '{role}', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);")
    output.extend(users_sql)
    output.append("")

    # 2. auth_db
    output.append("\\connect auth_db")
    output.append("DELETE FROM auth_schema.auth_accounts WHERE id >= 10000;")
    output.append("DELETE FROM auth_schema.refresh_tokens WHERE id >= 10000;")
    output.append("DELETE FROM auth_schema.email_otps WHERE id >= 10000;")
    auth_sql = []
    demo_hash = "$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z"
    for i in range(count):
        auth_id = base_id + i
        user_id = base_id + i
        phone = f"09100{i:05d}"
        auth_sql.append(f"INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified, phone_verified, status, created_at, updated_at) VALUES ({auth_id}, {user_id}, 'user{user_id}@laundry.test', '{phone}', '{demo_hash}', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);")
        auth_sql.append(f"INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at) VALUES ({auth_id}, {auth_id}, '{uuid.uuid4()}', CURRENT_TIMESTAMP + INTERVAL '30 days', CURRENT_TIMESTAMP);")
        auth_sql.append(f"INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at) VALUES ({auth_id}, 'user{user_id}@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes', CURRENT_TIMESTAMP);")
    output.extend(auth_sql)
    output.append("")

    # 3. store_db
    output.append("\\connect store_db")
    output.append("DELETE FROM store_schema.stores WHERE id >= 10000;")
    store_sql = []
    for i in range(count):
        store_id = base_id + i
        name = f"Smart Locker {random.choice(STREETS)} {i}"
        addr = gen_address()
        lat = 10.762622 + random.uniform(-0.05, 0.05)
        lng = 106.660172 + random.uniform(-0.05, 0.05)
        store_sql.append(f"INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at) VALUES ({store_id}, {escape_sql(name)}, {escape_sql(addr)}, {lat}, {lng}, '{gen_phone()}', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);")
    output.extend(store_sql)
    output.append("")

    # 4. locker_db
    output.append("\\connect locker_db")
    output.append("DELETE FROM locker_schema.lockers WHERE id >= 10000;")
    output.append("DELETE FROM locker_schema.locker_boxes WHERE id >= 10000;")
    output.append("DELETE FROM locker_schema.locker_reports WHERE id >= 10000;")
    output.append("DELETE FROM locker_schema.maintenance_schedules WHERE id >= 10000;")
    output.append("DELETE FROM locker_schema.repair_logs WHERE id >= 10000;")
    output.append("DELETE FROM locker_schema.locker_report_ratings WHERE id >= 10000;")
    locker_sql = []
    for i in range(count):
        locker_id = base_id + i
        store_id = base_id + i
        code = f"CAB-PROD-{1000+i}"
        name = f"Tủ tự động {i}"
        addr = f"{random.randint(1, 999)} {random.choice(STREETS)}"
        lat = 10.762622 + random.uniform(-0.05, 0.05)
        lng = 106.660172 + random.uniform(-0.05, 0.05)
        locker_sql.append(f"INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at, updated_at) VALUES ({locker_id}, {store_id}, '{code}', {escape_sql(name)}, 'ACTIVE', {escape_sql(addr)}, {lat}, {lng}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);")
        
        box_id = base_id + i
        size = random.choice(["SMALL", "MEDIUM", "LARGE"])
        locker_sql.append(f"INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at) VALUES ({box_id}, {locker_id}, {i+1}, '{size}', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);")

        report_id = base_id + i
        user_id = base_id + random.randint(0, count-1)
        reason = random.choice(["Kẹt cửa", "Màn hình đơ", "Mã PIN không chạy", "Mất nguồn"])
        locker_sql.append(f"INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at) VALUES ({report_id}, {locker_id}, {user_id}, 'Lỗi sự cố', {escape_sql(reason)}, 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);")
        
        locker_sql.append(f"INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at) VALUES ({report_id}, {report_id}, {user_id}, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);")
        locker_sql.append(f"INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at, updated_at) VALUES ({report_id}, {locker_id}, 'Bảo trì định kỳ {i}', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);")
        locker_sql.append(f"INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at) VALUES ({report_id}, {report_id}, {user_id}, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);")
    output.extend(locker_sql)
    output.append("")

    # 5. order_db
    output.append("\\connect order_db")
    output.append("DELETE FROM order_schema.orders WHERE id >= 10000;")
    output.append("DELETE FROM order_schema.order_details WHERE id >= 10000;")
    output.append("DELETE FROM order_schema.order_status_history WHERE id >= 10000;")
    output.append("DELETE FROM order_schema.order_ratings WHERE id >= 10000;")
    output.append("DELETE FROM order_schema.order_complaints WHERE id >= 10000;")
    output.append("DELETE FROM order_schema.promotions WHERE id >= 10000;")
    order_sql = []
    for i in range(count):
        order_id = base_id + i
        user_id = base_id + i
        store_id = base_id + i
        locker_id = base_id + i
        code = f"ORD{10000+i}"
        o_type = random.choice(["SEND", "RENTAL", "STORAGE"])
        status = random.choice(["PENDING", "COMPLETED", "CANCELED"])
        price = random.randint(10, 50) * 1000
        order_sql.append(f"INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at) VALUES ({order_id}, '{code}', {user_id}, '{o_type}', '{status}', {store_id}, {locker_id}, {price}, CURRENT_TIMESTAMP);")
        
        item_id = base_id + i
        order_sql.append(f"INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price) VALUES ({item_id}, {order_id}, {random.randint(1, 10)}, 1, {price});")
        order_sql.append(f"INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at) VALUES ({item_id}, {order_id}, '{status}', CURRENT_TIMESTAMP);")
        order_sql.append(f"INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at) VALUES ({item_id}, {order_id}, {user_id}, 5, CURRENT_TIMESTAMP);")
        order_sql.append(f"INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at) VALUES ({item_id}, {order_id}, {user_id}, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);")
        
        promo_id = base_id + i
        order_sql.append(f"INSERT INTO order_schema.promotions (id, code, name, discount_value, status) VALUES ({promo_id}, 'PROMO{promo_id}', 'Sale', 10000, 'ACTIVE');")
    output.extend(order_sql)
    output.append("")

    # 6. payment_db
    output.append("\\connect payment_db")
    output.append("DELETE FROM payment_schema.payments WHERE id >= 10000;")
    output.append("DELETE FROM payment_schema.refunds WHERE id >= 10000;")
    payment_sql = []
    for i in range(count):
        pay_id = base_id + i
        order_id = base_id + i
        amount = random.randint(10, 50) * 1000
        user_id = base_id + i
        payment_sql.append(f"INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id, created_at, updated_at) VALUES ({pay_id}, {order_id}, {user_id}, {amount}, 'VNPAY', 'COMPLETED', 'TXN{10000+i}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);")
        payment_sql.append(f"INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at) VALUES ({pay_id}, {pay_id}, {order_id}, {amount}, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);")
    output.extend(payment_sql)
    output.append("")

    # 7. iot_db
    output.append("\\connect iot_db")
    output.append("DELETE FROM iot_schema.device_statuses WHERE id >= 10000;")
    output.append("DELETE FROM iot_schema.box_access_logs WHERE id >= 10000;")
    output.append("DELETE FROM iot_schema.access_attempts WHERE box_id >= 10000;")
    iot_sql = []
    for i in range(count):
        iot_id = base_id + i
        locker_id = base_id + i
        box_id = base_id + i
        user_id = base_id + i
        iot_sql.append(f"INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at) VALUES ({iot_id}, 'dev_{iot_id}', {locker_id}, 'ONLINE', CURRENT_TIMESTAMP);")
        iot_sql.append(f"INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at) VALUES ({iot_id}, {box_id}, {user_id}, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);")
        iot_sql.append(f"INSERT INTO iot_schema.access_attempts (box_id, failed_count) VALUES ({box_id}, 1);")
    output.extend(iot_sql)
    output.append("")

    # 8. notification_db
    output.append("\\connect notification_db")
    output.append("DELETE FROM notification_schema.notifications WHERE id >= 10000;")
    output.append("DELETE FROM notification_schema.fcm_tokens WHERE id >= 10000;")
    noti_sql = []
    for i in range(count):
        noti_id = base_id + i
        user_id = base_id + i
        noti_sql.append(f"INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at) VALUES ({noti_id}, {user_id}, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);")
        noti_sql.append(f"INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at) VALUES ({noti_id}, {user_id}, 'fcm_{noti_id}', 'ANDROID', CURRENT_TIMESTAMP);")
    output.extend(noti_sql)
    output.append("")

    # 9. loyalty_db
    output.append("\\connect loyalty_db")
    output.append("DELETE FROM loyalty_schema.loyalty_accounts WHERE id >= 10000;")
    output.append("DELETE FROM loyalty_schema.point_transactions WHERE id >= 10000;")
    loyalty_sql = []
    for i in range(count):
        l_id = base_id + i
        user_id = base_id + i
        order_id = base_id + i
        loyalty_sql.append(f"INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at) VALUES ({l_id}, {user_id}, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);")
        loyalty_sql.append(f"INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at) VALUES ({l_id}, {user_id}, {order_id}, 50, 'EARN', CURRENT_TIMESTAMP);")
    output.extend(loyalty_sql)
    output.append("")
    
    # Update Sequences
    output.append("-- ==========================================")
    output.append("-- Reset sequences")
    output.append("-- ==========================================")
    output.append("\\connect user_db")
    output.append("SELECT setval('user_schema.user_profiles_id_seq', 11000);")
    output.append("\\connect auth_db")
    output.append("SELECT setval('auth_schema.auth_accounts_id_seq', 11000);")
    output.append("\\connect store_db")
    output.append("SELECT setval('store_schema.stores_id_seq', 11000);")
    output.append("\\connect locker_db")
    output.append("SELECT setval('locker_schema.lockers_id_seq', 11000);")
    output.append("\\connect order_db")
    output.append("SELECT setval('order_schema.orders_id_seq', 11000);")
    output.append("\\connect payment_db")
    output.append("SELECT setval('payment_schema.payments_id_seq', 11000);")
    output.append("\\connect iot_db")
    output.append("SELECT setval('iot_schema.device_statuses_id_seq', 11000);")
    output.append("\\connect notification_db")
    output.append("SELECT setval('notification_schema.notifications_id_seq', 11000);")
    output.append("\\connect loyalty_db")
    output.append("SELECT setval('loyalty_schema.loyalty_accounts_id_seq', 11000);")
    output.append("")

    with open("seed-100-realistic-data.sql", "w", encoding="utf-8") as f:
        f.write("\n".join(output))

if __name__ == "__main__":
    main()
