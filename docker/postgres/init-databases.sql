CREATE
USER auth_user WITH PASSWORD 'auth_pass';
CREATE
DATABASE auth_db OWNER auth_user;

CREATE
USER user_user WITH PASSWORD 'user_pass';
CREATE
DATABASE user_db OWNER user_user;

CREATE
USER order_user WITH PASSWORD 'order_pass';
CREATE
DATABASE order_db OWNER order_user;

CREATE
USER locker_user WITH PASSWORD 'locker_pass';
CREATE
DATABASE locker_db OWNER locker_user;

-- PA3: laundry_db gỡ khỏi init (laundry-service thiếu source, bị skip; DB rỗng).
-- partner_db gỡ khỏi init (role PARTNER đã bỏ). Drop trên DB đã chạy: scripts/drop-legacy-databases.sql

CREATE
USER payment_user WITH PASSWORD 'payment_pass';
CREATE
DATABASE payment_db OWNER payment_user;

CREATE
USER notification_user WITH PASSWORD 'notification_pass';
CREATE
DATABASE notification_db OWNER notification_user;

CREATE
USER iot_user WITH PASSWORD 'iot_pass';
CREATE
DATABASE iot_db OWNER iot_user;

CREATE
USER store_user WITH PASSWORD 'store_pass';
CREATE
DATABASE store_db OWNER store_user;

-- PA3 (đợt 2): staff_db gỡ khỏi init (staff-service đã bỏ). Drop trên DB đã chạy: scripts/drop-legacy-databases.sql

CREATE
USER loyalty_user WITH PASSWORD 'loyalty_pass';
CREATE
DATABASE loyalty_db OWNER loyalty_user;
