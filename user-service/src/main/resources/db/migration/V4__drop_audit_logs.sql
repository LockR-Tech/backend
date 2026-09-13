-- PA3 cleanup (đợt 2): drop audit_logs. Bảng chỉ được seed mẫu + có endpoint
-- admin đọc, KHÔNG có code runtime nào ghi (tính năng audit chưa từng hoàn
-- thiện). Gỡ luôn entity/repository/endpoint tương ứng.
DROP TABLE IF EXISTS user_schema.audit_logs CASCADE;
