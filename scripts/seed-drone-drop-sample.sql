-- ==============================================================================
-- Kịch bản tạo sẵn dữ liệu mẫu sự cố Drone rơi để kiểm tra UI Admin & Mobile KTV
-- Bảng áp dụng: order_schema.drone_parcel_incidents, evidence, timeline
-- Toạ độ điểm rơi: 10.841550, 106.810200 (Khuôn viên ĐH FPT, gần Tủ thông minh FPT-01)
-- ==============================================================================

-- 1. Sự cố 1: INC-2026-DROP-001 (Đang tìm kiếm - Dùng để test Bản đồ điều phối Radar tìm KTV bán kính R)
INSERT INTO order_schema.drone_parcel_incidents (
    incident_code,
    order_id,
    mission_id,
    drone_unit_id,
    drone_code,
    reported_by_user_id,
    idempotency_key,
    reason,
    status,
    parcel_status,
    recovery_status,
    inspection_status,
    redelivery_status,
    compensation_status,
    return_flight_status,
    drop_latitude,
    drop_longitude,
    gps_accuracy_m,
    gps_source,
    telemetry_observed_at,
    telemetry_stale,
    telemetry_json,
    camera_status,
    camera_snapshot_url,
    created_at,
    updated_at
) VALUES (
    'INC-2026-DROP-001',
    8801,
    501,
    101,
    'DRN-HCMC-09',
    1,
    'IDEMP-SAMPLE-DROP-001',
    'Drone gặp sự cố mất lực nâng cánh quạt số 3 khi cách điểm giao 350m. Kiện hàng rơi xuống bãi cỏ khuôn viên ĐH FPT (gần Tủ thông minh FPT-01). Hệ thống kích hoạt tìm kiếm KTV Kiosk & Drone gần nhất trong bán kính R.',
    'RECOVERY_IN_PROGRESS',
    'DROPPED',
    'ASSIGNED',
    'PENDING',
    'NOT_STARTED',
    'PENDING',
    'LANDED_EMERGENCY',
    10.841550,
    106.810200,
    2.5,
    'DRONE_TELEMETRY',
    NOW() - INTERVAL '20 minutes',
    FALSE,
    '{"altitudeM": 0.1, "speedKmh": 0.0, "batteryPercent": 42, "lastHealthWarning": "MOTOR_3_CURRENT_SPIKE"}',
    'ONLINE',
    'https://images.unsplash.com/photo-1527977966376-1c8408f9f108?w=800',
    NOW() - INTERVAL '25 minutes',
    NOW() - INTERVAL '5 minutes'
) ON CONFLICT (incident_code) DO NOTHING;

-- Timeline cho sự cố 1
INSERT INTO order_schema.drone_incident_timeline (
    incident_id,
    event_type,
    from_status,
    to_status,
    actor_user_id,
    note,
    created_at
)
SELECT 
    id,
    'DRONE_DROPPED_PARCEL',
    'DELIVERING',
    'RECOVERY_IN_PROGRESS',
    NULL,
    'Cảm biến gia tốc phát hiện rơi tự do từ độ cao 28m. Ngắt motor an toàn.',
    NOW() - INTERVAL '25 minutes'
FROM order_schema.drone_parcel_incidents 
WHERE incident_code = 'INC-2026-DROP-001'
ON CONFLICT DO NOTHING;

INSERT INTO order_schema.drone_incident_timeline (
    incident_id,
    event_type,
    from_status,
    to_status,
    actor_user_id,
    note,
    created_at
)
SELECT 
    id,
    'SEARCH_RADIUS_TRIGGERED',
    'REPORTED',
    'ASSIGNED',
    1,
    'Kích hoạt tìm kiếm KTV gần nhất trong bán kính R = 3km. Phát hiện 3 ứng viên KTV Kiosk và Drone.',
    NOW() - INTERVAL '22 minutes'
FROM order_schema.drone_parcel_incidents 
WHERE incident_code = 'INC-2026-DROP-001'
ON CONFLICT DO NOTHING;


-- 2. Sự cố 2: INC-2026-DROP-002 (Đã có KTV check-in toạ độ GPS, gửi ảnh & ghi chú - Dùng để test Màn hình Kiểm duyệt xác nhận của Admin)
INSERT INTO order_schema.drone_parcel_incidents (
    incident_code,
    order_id,
    mission_id,
    drone_unit_id,
    drone_code,
    reported_by_user_id,
    idempotency_key,
    reason,
    status,
    parcel_status,
    recovery_status,
    inspection_status,
    redelivery_status,
    compensation_status,
    return_flight_status,
    drop_latitude,
    drop_longitude,
    gps_accuracy_m,
    gps_source,
    telemetry_observed_at,
    telemetry_stale,
    telemetry_json,
    camera_status,
    camera_snapshot_url,
    recovery_assigned_to_user_id,
    recovery_outcome,
    parcel_condition,
    recovery_note,
    recovered_latitude,
    recovered_longitude,
    recovered_gps_accuracy_m,
    created_at,
    updated_at
) VALUES (
    'INC-2026-DROP-002',
    8802,
    502,
    102,
    'DRN-HCMC-14',
    1,
    'IDEMP-SAMPLE-DROP-002',
    'Gió giật mạnh tại toạ độ giao hàng khiến drone mất thăng bằng phải hạ cánh khẩn cấp. Kiện hàng rơi cách tủ FPT 75m.',
    'AWAITING_ADMIN_REVIEW',
    'RETRIEVED',
    'SUBMITTED',
    'COMPLETED',
    'NOT_STARTED',
    'PENDING',
    'RTH_COMPLETED',
    10.841550,
    106.810200,
    3.0,
    'DRONE_TELEMETRY',
    NOW() - INTERVAL '60 minutes',
    FALSE,
    '{"altitudeM": 0.0, "speedKmh": 0.0, "batteryPercent": 35}',
    'ONLINE',
    'https://images.unsplash.com/photo-1527977966376-1c8408f9f108?w=800',
    12,
    'FOUND',
    'INTACT',
    'Đã tiếp cận hiện trường và tìm thấy kiện hàng tại bãi cỏ gần hàng rào. Hộp carton còn nguyên seal niêm phong, không móp méo, mã vận đơn ORD-2026-8802 rõ ràng. Drone cánh số 2 bị gãy nhẹ đã được thu hồi kèm theo.',
    10.841590,
    106.810240,
    3.2,
    NOW() - INTERVAL '90 minutes',
    NOW() - INTERVAL '15 minutes'
) ON CONFLICT (incident_code) DO NOTHING;

-- Evidence ảnh chụp hiện trường cho sự cố 2
INSERT INTO order_schema.drone_incident_evidence (
    incident_id,
    stage,
    public_id,
    secure_url,
    caption,
    latitude,
    longitude,
    gps_accuracy_m,
    captured_at,
    uploaded_by_user_id,
    created_at
)
SELECT 
    id,
    'ON_SITE_RECOVERY',
    'evidence_drop_002_pkg',
    'https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?w=800',
    'Ảnh kiện hàng còn nguyên seal tại vị trí phát hiện (đối soát GPS: chênh lệch 6.1m)',
    10.841590,
    106.810240,
    3.2,
    NOW() - INTERVAL '25 minutes',
    12,
    NOW() - INTERVAL '25 minutes'
FROM order_schema.drone_parcel_incidents 
WHERE incident_code = 'INC-2026-DROP-002'
ON CONFLICT (public_id) DO NOTHING;

INSERT INTO order_schema.drone_incident_evidence (
    incident_id,
    stage,
    public_id,
    secure_url,
    caption,
    latitude,
    longitude,
    gps_accuracy_m,
    captured_at,
    uploaded_by_user_id,
    created_at
)
SELECT 
    id,
    'ON_SITE_RECOVERY',
    'evidence_drop_002_scene',
    'https://images.unsplash.com/photo-1508614589041-895b88991e3e?w=800',
    'Toàn cảnh hiện trường bãi cỏ quanh khu vực rơi',
    10.841592,
    106.810238,
    4.0,
    NOW() - INTERVAL '24 minutes',
    12,
    NOW() - INTERVAL '24 minutes'
FROM order_schema.drone_parcel_incidents 
WHERE incident_code = 'INC-2026-DROP-002'
ON CONFLICT (public_id) DO NOTHING;

-- Timeline cho sự cố 2
INSERT INTO order_schema.drone_incident_timeline (
    incident_id,
    event_type,
    from_status,
    to_status,
    actor_user_id,
    note,
    created_at
)
SELECT 
    id,
    'RECOVERY_SUBMITTED',
    'SEARCHING',
    'SUBMITTED',
    12,
    'KTV check-in toạ độ GPS tại hiện trường (sai số 6.1m so với điểm rơi), gửi 2 ảnh bằng chứng và báo cáo kiện nguyên vẹn.',
    NOW() - INTERVAL '20 minutes'
FROM order_schema.drone_parcel_incidents 
WHERE incident_code = 'INC-2026-DROP-002'
ON CONFLICT DO NOTHING;
