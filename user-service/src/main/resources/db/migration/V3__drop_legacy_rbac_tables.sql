-- PA3 cleanup: gỡ 3 bảng RBAC orphan.
-- Phân quyền thực tế dùng cột `user_schema.user_profiles.roles` (VARCHAR) +
-- claim `roles` trong JWT; 3 bảng dưới không có entity/repository nào query,
-- chỉ từng được seed. An toàn để bỏ.
DROP TABLE IF EXISTS user_schema.role_permissions CASCADE;
DROP TABLE IF EXISTS user_schema.roles CASCADE;
DROP TABLE IF EXISTS user_schema.permissions CASCADE;
