-- Seed dữ liệu ca trực và vị trí GPS cho KTV xung quanh khu vực Trường Đại học FPT TP.HCM
-- (Lô E2a-7, Đường D1, Khu Công Nghệ Cao, P. Long Thạnh Mỹ, TP. Thủ Đức, TP.HCM) và khu vực lân cận.
INSERT INTO locker_schema.technician_presences
(user_id, technician_type, on_duty, latitude, longitude, accuracy_m, heading_deg, speed_ms, altitude_m, battery_level, device_info, shift_started_at, shift_ended_at, last_located_at, created_at, updated_at)
VALUES
    -- KTV 3 (DRONE_TECHNICIAN): Nguyễn Quốc Bảo Huy - Đang trong ca tại Cổng chính ĐH FPT (Tòa Alpha)
    (3, 'DRONE_TECHNICIAN', TRUE, 10.841122, 106.809783, 4.5, 90.0, 0.0, 18.5, 92,
     'Cổng chính ĐH FPT Tòa Alpha, Khu Công Nghệ Cao', NOW() - INTERVAL '3 hours', NULL, NOW() - INTERVAL '2 minutes', NOW() - INTERVAL '3 hours', NOW()),

    -- KTV 4 (LOCKER_TECHNICIAN): Huỳnh Quốc Bảo Huy - Đang trong ca tại Tòa Gamma & KTX ĐH FPT
    (4, 'LOCKER_TECHNICIAN', TRUE, 10.842415, 106.808540, 5.0, 180.0, 0.5, 19.0, 86,
     'Tòa nhà Gamma & KTX ĐH FPT, Đường D1 Khu CNC', NOW() - INTERVAL '4 hours', NULL, NOW() - INTERVAL '5 minutes', NOW() - INTERVAL '4 hours', NOW()),

    -- KTV 5: Đang trong ca tại Vinhomes Grand Park (Sảnh S5.02)
    (5, 'LOCKER_TECHNICIAN', TRUE, 10.843210, 106.828640, 4.0, 45.0, 0.0, 17.5, 88,
     'Sảnh S5.02 Vinhomes Grand Park, P. Long Thạnh Mỹ', NOW() - INTERVAL '2 hours', NULL, NOW() - INTERVAL '4 minutes', NOW() - INTERVAL '2 hours', NOW()),

    -- KTV 7: Ngoài ca tại Khu Công Nghệ Cao (Đường D2)
    (7, 'DRONE_TECHNICIAN', FALSE, 10.845240, 106.794510, 6.0, 0.0, 0.0, 16.0, 75,
     'Đường D2 - Khu Công Nghệ Cao (gần ĐH FPT)', NOW() - INTERVAL '9 hours', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '9 hours', NOW()),

    -- KTV 11 (LOCKER_TECHNICIAN): Trương Nguyễn Thái Bình - Đang trong ca tại Nguyễn Văn Tăng
    (11, 'LOCKER_TECHNICIAN', TRUE, 10.846820, 106.819540, 5.5, 270.0, 1.2, 18.0, 81,
     '245 Nguyễn Văn Tăng, P. Long Thạnh Mỹ (gần ĐH FPT)', NOW() - INTERVAL '5 hours', NULL, NOW() - INTERVAL '7 minutes', NOW() - INTERVAL '5 hours', NOW()),

    -- KTV 13: Đang trong ca tại Hoàng Hữu Nam (Cầu Xây)
    (13, 'DRONE_TECHNICIAN', TRUE, 10.848610, 106.813240, 4.8, 120.0, 0.8, 17.0, 79,
     'Đường Hoàng Hữu Nam - Cầu Xây, P. Long Thạnh Mỹ', NOW() - INTERVAL '1 hour', NULL, NOW() - INTERVAL '3 minutes', NOW() - INTERVAL '1 hour', NOW()),

    -- KTV 14: Ngoài ca tại Vincom Lê Văn Việt
    (14, 'LOCKER_TECHNICIAN', FALSE, 10.844620, 106.779240, 7.0, 0.0, 0.0, 20.0, 68,
     'Vincom Lê Văn Việt, P. Hiệp Phú, TP. Thủ Đức', NOW() - INTERVAL '10 hours', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '10 hours', NOW()),

    -- KTV 9003: Đang trong ca tại Cổng chính ĐH FPT Tòa Alpha
    (9003, 'DRONE_TECHNICIAN', TRUE, 10.841122, 106.809783, 4.2, 90.0, 0.0, 18.5, 94,
     'Cổng chính ĐH FPT Tòa Alpha, Khu Công Nghệ Cao', NOW() - INTERVAL '2 hours', NULL, NOW() - INTERVAL '3 minutes', NOW() - INTERVAL '2 hours', NOW()),

    -- KTV 9004: Đang trong ca tại Tòa Gamma & KTX ĐH FPT
    (9004, 'LOCKER_TECHNICIAN', TRUE, 10.842415, 106.808540, 4.8, 180.0, 0.0, 19.0, 89,
     'Tòa nhà Gamma & KTX ĐH FPT, Đường D1 Khu CNC', NOW() - INTERVAL '3 hours', NULL, NOW() - INTERVAL '6 minutes', NOW() - INTERVAL '3 hours', NOW()),

    -- KTV 10007: Đang trong ca tại Sảnh S5.02 Vinhomes Grand Park
    (10007, 'DRONE_TECHNICIAN', TRUE, 10.843210, 106.828640, 5.0, 45.0, 0.5, 17.5, 87,
     'Sảnh S5.02 Vinhomes Grand Park, P. Long Thạnh Mỹ', NOW() - INTERVAL '3 hours', NULL, NOW() - INTERVAL '5 minutes', NOW() - INTERVAL '3 hours', NOW()),

    -- KTV 10017: Đang trong ca tại Phân khu The Origami
    (10017, 'DRONE_TECHNICIAN', TRUE, 10.849150, 106.836210, 4.5, 90.0, 1.1, 16.5, 83,
     'Phân khu The Origami, Vinhomes Grand Park', NOW() - INTERVAL '4 hours', NULL, NOW() - INTERVAL '8 minutes', NOW() - INTERVAL '4 hours', NOW()),

    -- KTV 10018: Ngoài ca tại Ga Metro ĐHQG
    (10018, 'DRONE_TECHNICIAN', FALSE, 10.865420, 106.803120, 6.2, 0.0, 0.0, 22.0, 72,
     'Ga Metro ĐHQG TP.HCM - Xa Lộ Hà Nội', NOW() - INTERVAL '8 hours', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '8 hours', NOW()),

    -- KTV 10033: Đang trong ca tại Nhà Văn hóa Sinh viên ĐHQG
    (10033, 'DRONE_TECHNICIAN', TRUE, 10.875510, 106.800740, 5.0, 180.0, 0.0, 25.0, 85,
     'Nhà Văn hóa Sinh viên ĐHQG TP.HCM', NOW() - INTERVAL '2 hours', NULL, NOW() - INTERVAL '6 minutes', NOW() - INTERVAL '2 hours', NOW()),

    -- KTV 10037: Đang trong ca tại Sân bóng Tòa Beta ĐH FPT
    (10037, 'DRONE_TECHNICIAN', TRUE, 10.841850, 106.810420, 4.0, 270.0, 0.4, 18.0, 91,
     'Tòa Beta & Sân bóng ĐH FPT TP.HCM, Khu CNC', NOW() - INTERVAL '1 hour', NULL, NOW() - INTERVAL '1 minute', NOW() - INTERVAL '1 hour', NOW()),

    -- KTV 10038: Ngoài ca tại Nguyễn Văn Tăng
    (10038, 'DRONE_TECHNICIAN', FALSE, 10.846820, 106.819540, 5.8, 0.0, 0.0, 18.0, 69,
     '245 Nguyễn Văn Tăng, P. Long Thạnh Mỹ (gần ĐH FPT)', NOW() - INTERVAL '9 hours', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '9 hours', NOW()),

    -- KTV 10046: Đang trong ca tại Cổng chính ĐH FPT
    (10046, 'DRONE_TECHNICIAN', TRUE, 10.841122, 106.809783, 4.1, 90.0, 0.0, 18.5, 93,
     'Cổng chính ĐH FPT Tòa Alpha, Khu Công Nghệ Cao', NOW() - INTERVAL '4 hours', NULL, NOW() - INTERVAL '4 minutes', NOW() - INTERVAL '4 hours', NOW()),

    -- KTV 10050: Đang trong ca tại Vinhomes Grand Park
    (10050, 'DRONE_TECHNICIAN', TRUE, 10.843210, 106.828640, 4.9, 0.0, 0.0, 17.5, 84,
     'Sảnh S5.02 Vinhomes Grand Park, P. Long Thạnh Mỹ', NOW() - INTERVAL '3 hours', NULL, NOW() - INTERVAL '5 minutes', NOW() - INTERVAL '3 hours', NOW()),

    -- KTV 10051: Ngoài ca tại Tòa Gamma ĐH FPT
    (10051, 'DRONE_TECHNICIAN', FALSE, 10.842415, 106.808540, 6.0, 0.0, 0.0, 19.0, 77,
     'Tòa nhà Gamma & KTX ĐH FPT, Đường D1 Khu CNC', NOW() - INTERVAL '7 hours', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '7 hours', NOW()),

    -- KTV 10054: Đang trong ca tại Đường D2 Khu CNC
    (10054, 'DRONE_TECHNICIAN', TRUE, 10.845240, 106.794510, 5.2, 180.0, 0.9, 16.0, 82,
     'Đường D2 - Khu Công Nghệ Cao (gần ĐH FPT)', NOW() - INTERVAL '2 hours', NULL, NOW() - INTERVAL '3 minutes', NOW() - INTERVAL '2 hours', NOW()),

    -- KTV 10073: Đang trong ca tại Phân khu The Origami
    (10073, 'DRONE_TECHNICIAN', TRUE, 10.849150, 106.836210, 4.7, 90.0, 0.0, 16.5, 88,
     'Phân khu The Origami, Vinhomes Grand Park', NOW() - INTERVAL '3 hours', NULL, NOW() - INTERVAL '7 minutes', NOW() - INTERVAL '3 hours', NOW()),

    -- KTV 10080: Ngoài ca tại Vincom Lê Văn Việt
    (10080, 'DRONE_TECHNICIAN', FALSE, 10.844620, 106.779240, 6.5, 0.0, 0.0, 20.0, 71,
     'Vincom Lê Văn Việt, P. Hiệp Phú, TP. Thủ Đức', NOW() - INTERVAL '8 hours', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '8 hours', NOW()),

    -- KTV 10083: Đang trong ca tại Ga Metro ĐHQG
    (10083, 'DRONE_TECHNICIAN', TRUE, 10.865420, 106.803120, 5.4, 45.0, 1.0, 22.0, 86,
     'Ga Metro ĐHQG TP.HCM - Xa Lộ Hà Nội', NOW() - INTERVAL '4 hours', NULL, NOW() - INTERVAL '6 minutes', NOW() - INTERVAL '4 hours', NOW()),

    -- KTV 10085: Đang trong ca tại Cổng chính ĐH FPT Tòa Alpha
    (10085, 'DRONE_TECHNICIAN', TRUE, 10.841122, 106.809783, 4.3, 90.0, 0.0, 18.5, 95,
     'Cổng chính ĐH FPT Tòa Alpha, Khu Công Nghệ Cao', NOW() - INTERVAL '1 hour', NULL, NOW() - INTERVAL '2 minutes', NOW() - INTERVAL '1 hour', NOW()),

    -- KTV 10087: Ngoài ca tại Sân bóng Tòa Beta ĐH FPT
    (10087, 'DRONE_TECHNICIAN', FALSE, 10.841850, 106.810420, 5.0, 0.0, 0.0, 18.0, 76,
     'Tòa Beta & Sân bóng ĐH FPT TP.HCM, Khu CNC', NOW() - INTERVAL '6 hours', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '6 hours', NOW()),

    -- KTV 10088: Đang trong ca tại Tòa Gamma & KTX ĐH FPT
    (10088, 'DRONE_TECHNICIAN', TRUE, 10.842415, 106.808540, 4.6, 180.0, 0.0, 19.0, 90,
     'Tòa nhà Gamma & KTX ĐH FPT, Đường D1 Khu CNC', NOW() - INTERVAL '2 hours', NULL, NOW() - INTERVAL '4 minutes', NOW() - INTERVAL '2 hours', NOW()),

    -- KTV 10089: Đang trong ca tại Vinhomes Grand Park S5.02
    (10089, 'DRONE_TECHNICIAN', TRUE, 10.843210, 106.828640, 5.1, 45.0, 0.7, 17.5, 83,
     'Sảnh S5.02 Vinhomes Grand Park, P. Long Thạnh Mỹ', NOW() - INTERVAL '3 hours', NULL, NOW() - INTERVAL '5 minutes', NOW() - INTERVAL '3 hours', NOW()),

    -- KTV 10091: Ngoài ca tại Hoàng Hữu Nam (Cầu Xây)
    (10091, 'DRONE_TECHNICIAN', FALSE, 10.848610, 106.813240, 6.1, 0.0, 0.0, 17.0, 73,
     'Đường Hoàng Hữu Nam - Cầu Xây, P. Long Thạnh Mỹ', NOW() - INTERVAL '8 hours', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '8 hours', NOW()),

    -- KTV 10098: Đang trong ca tại Cổng chính ĐH FPT Tòa Alpha
    (10098, 'DRONE_TECHNICIAN', TRUE, 10.841122, 106.809783, 4.4, 90.0, 0.0, 18.5, 91,
     'Cổng chính ĐH FPT Tòa Alpha, Khu Công Nghệ Cao', NOW() - INTERVAL '2 hours', NULL, NOW() - INTERVAL '3 minutes', NOW() - INTERVAL '2 hours', NOW())
ON CONFLICT (user_id) DO UPDATE SET
    technician_type = EXCLUDED.technician_type,
    on_duty = EXCLUDED.on_duty,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude,
    accuracy_m = EXCLUDED.accuracy_m,
    heading_deg = EXCLUDED.heading_deg,
    speed_ms = EXCLUDED.speed_ms,
    altitude_m = EXCLUDED.altitude_m,
    battery_level = EXCLUDED.battery_level,
    device_info = EXCLUDED.device_info,
    shift_started_at = EXCLUDED.shift_started_at,
    shift_ended_at = EXCLUDED.shift_ended_at,
    last_located_at = EXCLUDED.last_located_at,
    updated_at = NOW();
