-- Hoan thien luong bao tri drone:
--  #2 phieu su co cho drone (locker_reports.drone_unit_id, box_id van NULL)
--  #3 lich bao tri dinh ky co the gan cho drone (maintenance_schedules.drone_unit_id)
--  #4 vong doi drone: co ngung hoat dong (drone_units.active)
--  #6 trang thai bao tri bai dap drone tren tung tu (lockers.landing_pad_status)

-- #4 soft-decommission: drone ngung hoat dong khong hien trong danh sach van hanh
ALTER TABLE locker_schema.drone_units
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- #2 lien ket 1 phieu su co voi 1 drone (box_id giu NULL cho su co drone)
ALTER TABLE locker_schema.locker_reports
    ADD COLUMN drone_unit_id BIGINT;
ALTER TABLE locker_schema.locker_reports
    ADD CONSTRAINT fk_locker_reports_drone
        FOREIGN KEY (drone_unit_id) REFERENCES locker_schema.drone_units (id);
CREATE INDEX idx_locker_reports_drone ON locker_schema.locker_reports (drone_unit_id);

-- #3 lich dinh ky co the nham vao 1 drone thay vi 1 tu -> locker_id cho phep NULL
ALTER TABLE locker_schema.maintenance_schedules
    ALTER COLUMN locker_id DROP NOT NULL;
ALTER TABLE locker_schema.maintenance_schedules
    ADD COLUMN drone_unit_id BIGINT;
ALTER TABLE locker_schema.maintenance_schedules
    ADD CONSTRAINT fk_maintenance_schedules_drone
        FOREIGN KEY (drone_unit_id) REFERENCES locker_schema.drone_units (id);
CREATE INDEX idx_maintenance_schedules_drone ON locker_schema.maintenance_schedules (drone_unit_id);

-- #6 trang thai bai dap drone tren noc tu: OK / FAULT / MAINTENANCE
ALTER TABLE locker_schema.lockers
    ADD COLUMN landing_pad_status VARCHAR(20) NOT NULL DEFAULT 'OK';
