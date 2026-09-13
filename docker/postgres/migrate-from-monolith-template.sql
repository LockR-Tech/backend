-- Data migration template from the old monolith schema to split service databases.
--
-- Assumptions:
-- 1. The old monolith PostgreSQL database is reachable from the target PostgreSQL server.
-- 2. The old schema name is `laundry_locker_schema`.
-- 3. Run each section while connected to the target service database.
-- 4. Replace the dblink connection string before running.
--
-- Example:
--   psql -h localhost -p 15432 -U user_user -d user_db -f docker/postgres/migrate-from-monolith-template.sql
--
-- This template intentionally does not add cross-database FK constraints.

CREATE
EXTENSION IF NOT EXISTS dblink;

-- ===== user_db / user_schema =====
-- INSERT INTO user_schema.user_profiles(id, email, phone_number, first_name, last_name, birthday, image_url, status, roles, created_at, updated_at)
-- SELECT id, email, phone_number, split_part(name, ' ', 1), null, birthday, image, status, 'USER', created_at, updated_at
-- FROM dblink('host=HOST port=5432 dbname=laundry_locker user=USER password=PASSWORD',
--   'select id,email,phone_number,name,birthday,image,status,created_at,updated_at from laundry_locker_schema.users')
-- AS t(id bigint, email varchar, phone_number varchar, name varchar, birthday date, image varchar, status varchar, created_at timestamp, updated_at timestamp);

-- ===== store_db / store_schema =====
-- INSERT INTO store_schema.stores(id, partner_id, name, contact_phone, address, latitude, longitude, image, description, is_active, status, created_at, updated_at)
-- SELECT id, partner_id, name, contact_phone, address, latitude, longitude, image, description, is_active, status, created_at, updated_at
-- FROM dblink('host=HOST port=5432 dbname=laundry_locker user=USER password=PASSWORD',
--   'select id,partner_id,name,contact_phone,address,latitude,longitude,image,description,is_active,status,created_at,updated_at from laundry_locker_schema.stores')
-- AS t(id bigint, partner_id bigint, name varchar, contact_phone varchar, address varchar, latitude double precision, longitude double precision, image varchar, description varchar, is_active boolean, status varchar, created_at timestamp, updated_at timestamp);

-- ===== laundry_db / laundry_schema =====
-- INSERT INTO laundry_schema.laundry_catalog_items(id, store_id, name, category, service_type, unit_price, max_price, unit, description, image, is_addon, is_monthly_package, estimated_hours, status, created_at, updated_at)
-- SELECT id, store_id, name, category, service_type, price, max_price, unit, description, image, is_addon, is_monthly_package, estimated_hours, status, created_at, updated_at
-- FROM dblink('host=HOST port=5432 dbname=laundry_locker user=USER password=PASSWORD',
--   'select id,store_id,name,category,service_type,price,max_price,unit,description,image,is_addon,is_monthly_package,estimated_hours,status,created_at,updated_at from laundry_locker_schema.services')
-- AS t(id bigint, store_id bigint, name varchar, category varchar, service_type varchar, price numeric, max_price numeric, unit varchar, description varchar, image varchar, is_addon boolean, is_monthly_package boolean, estimated_hours integer, status varchar, created_at timestamp, updated_at timestamp);

-- ===== locker_db / locker_schema =====
-- Map old lockers/boxes/reports to locker-owned tables. Store/user references are copied as IDs only.

-- ===== order_db / order_schema =====
-- Map old orders/order_details/status_history/ratings/complaints/promotions. User/locker/box/service refs are copied as IDs only.

-- ===== payment_db / payment_schema =====
-- Map old payments/refunds. Order/customer/processor refs are copied as IDs only.

-- ===== notification_db / notification_schema =====
-- Map old notifications/fcm_tokens. User refs are copied as user_id only.

-- ===== partner_db / partner_schema =====
-- Map old partners/staff_access_codes. User/order refs are copied as IDs only.

-- ===== loyalty_db / loyalty_schema =====
-- Map old loyalty_accounts/point_transactions/stamp_cards/stamp_transactions. User/order/service refs are copied as IDs only.

