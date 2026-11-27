-- F3-G16: bản đầu ghi occurred_at theo giờ Việt Nam, trong khi mọi cột thời gian khác (và web) dùng giờ UTC ⇒ web
-- hiện lệch +7 giờ. Từ bản này user-service ghi giờ UTC; đổi các dòng đã có (đều do bản đầu ghi) về UTC.
UPDATE user_schema.action_logs SET occurred_at = occurred_at - INTERVAL '7 hours';
