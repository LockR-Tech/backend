-- Assigning unique ArUco markers to DRONE-enabled locker boxes
ALTER TABLE locker_schema.locker_boxes
    ADD COLUMN aruco_dictionary VARCHAR(50) DEFAULT 'DICT_4X4_50',
    ADD COLUMN aruco_marker_id INTEGER,
    ADD COLUMN aruco_marker_size_cm NUMERIC(5, 2) DEFAULT 8.00,
    ADD COLUMN aruco_assigned_at TIMESTAMP,
    ADD COLUMN aruco_assigned_by VARCHAR(100);

-- Ensure a marker ID in a dictionary is unique across all locker boxes
CREATE UNIQUE INDEX idx_locker_boxes_aruco_unique
    ON locker_schema.locker_boxes (aruco_dictionary, aruco_marker_id)
    WHERE aruco_marker_id IS NOT NULL;

