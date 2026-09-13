\set
ON_ERROR_STOP on

\connect auth_db
SELECT 'auth_schema.auth_accounts' AS table_name, COUNT(*) AS row_count
FROM auth_schema.auth_accounts;
SELECT 'auth_schema.refresh_tokens' AS table_name, COUNT(*) AS row_count
FROM auth_schema.refresh_tokens;
SELECT 'auth_schema.email_otps' AS table_name, COUNT(*) AS row_count
FROM auth_schema.email_otps;

\connect
user_db
SELECT 'user_schema.user_profiles' AS table_name, COUNT(*) AS row_count
FROM user_schema.user_profiles;

\connect
order_db
SELECT 'order_schema.orders' AS table_name, COUNT(*) AS row_count
FROM order_schema.orders;
SELECT 'order_schema.order_details' AS table_name, COUNT(*) AS row_count
FROM order_schema.order_details;
SELECT 'order_schema.order_status_history' AS table_name, COUNT(*) AS row_count
FROM order_schema.order_status_history;
SELECT 'order_schema.order_ratings' AS table_name, COUNT(*) AS row_count
FROM order_schema.order_ratings;
SELECT 'order_schema.order_complaints' AS table_name, COUNT(*) AS row_count
FROM order_schema.order_complaints;
SELECT 'order_schema.promotions' AS table_name, COUNT(*) AS row_count
FROM order_schema.promotions;

\connect
locker_db
SELECT 'locker_schema.lockers' AS table_name, COUNT(*) AS row_count
FROM locker_schema.lockers;
SELECT 'locker_schema.locker_boxes' AS table_name, COUNT(*) AS row_count
FROM locker_schema.locker_boxes;
SELECT 'locker_schema.locker_reports' AS table_name, COUNT(*) AS row_count
FROM locker_schema.locker_reports;

\connect
laundry_db
SELECT 'laundry_schema.laundry_catalog_items' AS table_name, COUNT(*) AS row_count
FROM laundry_schema.laundry_catalog_items;

\connect
payment_db
SELECT 'payment_schema.payments' AS table_name, COUNT(*) AS row_count
FROM payment_schema.payments;
SELECT 'payment_schema.refunds' AS table_name, COUNT(*) AS row_count
FROM payment_schema.refunds;

\connect
notification_db
SELECT 'notification_schema.notifications' AS table_name, COUNT(*) AS row_count
FROM notification_schema.notifications;
SELECT 'notification_schema.fcm_tokens' AS table_name, COUNT(*) AS row_count
FROM notification_schema.fcm_tokens;

\connect
iot_db
SELECT 'iot_schema.device_statuses' AS table_name, COUNT(*) AS row_count
FROM iot_schema.device_statuses;

\connect
store_db
SELECT 'store_schema.stores' AS table_name, COUNT(*) AS row_count
FROM store_schema.stores;

\connect
staff_db
SELECT 'staff_schema.staff_assignments' AS table_name, COUNT(*) AS row_count
FROM staff_schema.staff_assignments;

\connect
loyalty_db
SELECT 'loyalty_schema.loyalty_accounts' AS table_name, COUNT(*) AS row_count
FROM loyalty_schema.loyalty_accounts;
SELECT 'loyalty_schema.point_transactions' AS table_name, COUNT(*) AS row_count
FROM loyalty_schema.point_transactions;
