-- =============================================
-- Seed Demo Accounts for Microservices
-- Run on the MICROSERVICES stack (auth_db + user_db)
--
-- Accounts:
--   demo@laundry.test       / secret123     → USER (id=1)
--   admin@laundry.test      / Admin@123456  → ADMIN (id=2)
--   manager@laundry.test    / Manager@123456→ MANAGER (id=5)
--   maintenance@laundry.test/ Maint@123456  → MAINTENANCE (id=7)
--
-- BCrypt hashes generated with strength=10
-- =============================================

-- ─────────────────────────────────────────────
-- 1. Seed user_db (user_schema.user_profiles)
-- ─────────────────────────────────────────────
\connect
user_db
SET search_path TO user_schema;

INSERT INTO user_schema.user_profiles (id, email, phone_number, first_name, last_name,
                                       birthday, status, roles, created_at, updated_at)
VALUES (1, 'demo@laundry.test', '0900000001', 'Demo', 'Customer', DATE '2000-01-01', 'ACTIVE', 'USER', NOW(), NOW()),
       (2, 'admin@laundry.test', '0900000002', 'Admin', 'System', DATE '1990-01-01', 'ACTIVE', 'ADMIN', NOW(), NOW()),
       (5, 'manager@laundry.test', '0900000005', 'Manager', 'Store', DATE '1990-01-01', 'ACTIVE', 'MANAGER', NOW(),
        NOW()),
       (7, 'maintenance@laundry.test', '0900000007', 'Maintenance', 'Team', DATE '1990-01-01', 'ACTIVE', 'MAINTENANCE',
        NOW(), NOW()) ON CONFLICT (id) DO
UPDATE SET
    email = EXCLUDED.email,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    roles = EXCLUDED.roles,
    status = 'ACTIVE',
    updated_at = NOW();

-- ─────────────────────────────────────────────
-- 2. Seed auth_db (auth_schema.auth_accounts)
-- ─────────────────────────────────────────────
\connect
auth_db
SET search_path TO auth_schema;

-- BCrypt hashes (strength=10):
--   secret123     → $2a$10$XZ6O1E1lHJBFnfwFHqGlEuROvN77JMkfQMDGQJXJN60RNPpkf5Q2m
--   Admin@123456  → $2a$10$N0vk.FhZkTDGGE3t0tAWN.KtmtV8s3XJV2f2v.qblRoD6w.Ck2P4K
--   Manager@123456→ $2a$10$7LPu.cBwQmC8f3Y4m3d0jOdvLR6j5e5M7a7j.FBF4JK9D2sGm0sxa
--   Maint@123456  → $2a$10$kJ5H8q0Y3vZ.R2gX1mB4bOX2e.l4s4K0m9n7Q2oT1l5b8A3mG6G2a

INSERT INTO auth_schema.auth_accounts (user_id, email, phone_number, password_hash,
                                       auth_provider, email_verified, phone_verified, status,
                                       last_login_at, created_at, updated_at)
VALUES (1, 'demo@laundry.test', '0900000001',
        '$2a$10$XZ6O1E1lHJBFnfwFHqGlEuROvN77JMkfQMDGQJXJN60RNPpkf5Q2m',
        'LOCAL', TRUE, TRUE, 'ACTIVE', NOW(), NOW(), NOW()),

       (2, 'admin@laundry.test', '0900000002',
        '$2a$10$N0vk.FhZkTDGGE3t0tAWN.KtmtV8s3XJV2f2v.qblRoD6w.Ck2P4K',
        'LOCAL', TRUE, TRUE, 'ACTIVE', NOW(), NOW(), NOW()),

       (5, 'manager@laundry.test', '0900000005',
        '$2a$10$7LPu.cBwQmC8f3Y4m3d0jOdvLR6j5e5M7a7j.FBF4JK9D2sGm0sxa',
        'LOCAL', TRUE, TRUE, 'ACTIVE', NOW(), NOW(), NOW()),

       (7, 'maintenance@laundry.test', '0900000007',
        '$2a$10$kJ5H8q0Y3vZ.R2gX1mB4bOX2e.l4s4K0m9n7Q2oT1l5b8A3mG6G2a',
        'LOCAL', TRUE, TRUE, 'ACTIVE', NOW(), NOW(), NOW()) ON CONFLICT (user_id) DO
UPDATE SET
    email = EXCLUDED.email,
    password_hash = EXCLUDED.password_hash,
    status = 'ACTIVE',
    email_verified = TRUE,
    phone_verified = TRUE,
    updated_at = NOW();

\echo
'✅  Demo accounts seeded successfully!'
\echo '   demo@laundry.test        / secret123'
\echo '   admin@laundry.test       / Admin@123456'
\echo '   manager@laundry.test     / Manager@123456'
\echo '   maintenance@laundry.test / Maint@123456'
