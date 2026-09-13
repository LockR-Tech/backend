-- ==========================================
-- SEED 100 REALISTIC RECORDS PER TABLE
-- ==========================================

\connect
user_db
DELETE
FROM user_schema.user_profiles
WHERE id >= 10000;
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10000, '0924604196', 'user10000@laundry.test', 'Nguyễn', 'Thị Bình',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10000', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10001, '0991986295', 'user10001@laundry.test', 'Đặng', 'Ngọc Minh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10001', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10002, '0918502661', 'user10002@laundry.test', 'Phan', 'Tuấn Vinh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10002', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10003, '0964469492', 'user10003@laundry.test', 'Lý', 'Thanh Đạt',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10003', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10004, '0907787022', 'user10004@laundry.test', 'Dương', 'Văn Sang',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10004', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10005, '0938611793', 'user10005@laundry.test', 'Đỗ', 'Bảo Ngọc',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10005', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10006, '0991278677', 'user10006@laundry.test', 'Ngô', 'Văn Hùng',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10006', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10007, '0954565159', 'user10007@laundry.test', 'Dương', 'Thu Trang',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10007', 'MAINTENANCE', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10008, '0959778477', 'user10008@laundry.test', 'Lý', 'Thu Hùng',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10008', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10009, '0934714231', 'user10009@laundry.test', 'Phạm', 'Hữu Tuấn',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10009', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10010, '0919715955', 'user10010@laundry.test', 'Bùi', 'Văn Anh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10010', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10011, '0964148059', 'user10011@laundry.test', 'Ngô', 'Văn Hùng',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10011', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10012, '0947677115', 'user10012@laundry.test', 'Trần', 'Thanh Uyên',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10012', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10013, '0954956029', 'user10013@laundry.test', 'Phan', 'Văn Uyên',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10013', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10014, '0989350895', 'user10014@laundry.test', 'Đặng', 'Hữu Tuấn',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10014', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10015, '0980696440', 'user10015@laundry.test', 'Bùi', 'Bảo Oanh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10015', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10016, '0986128270', 'user10016@laundry.test', 'Đỗ', 'Bảo Oanh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10016', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10017, '0919670846', 'user10017@laundry.test', 'Bùi', 'Ngọc Linh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10017', 'MAINTENANCE', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10018, '0961819457', 'user10018@laundry.test', 'Phạm', 'Thanh Bình',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10018', 'MAINTENANCE', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10019, '0964004815', 'user10019@laundry.test', 'Dương', 'Văn Hải',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10019', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10020, '0953624911', 'user10020@laundry.test', 'Dương', 'Bảo Phong',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10020', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10021, '0984814544', 'user10021@laundry.test', 'Phan', 'Ngọc Uyên',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10021', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10022, '0933341101', 'user10022@laundry.test', 'Nguyễn', 'Thị Dương',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10022', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10023, '0994379483', 'user10023@laundry.test', 'Hồ', 'Tuấn Linh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10023', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10024, '0925890280', 'user10024@laundry.test', 'Lê', 'Tuấn Khánh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10024', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10025, '0906404083', 'user10025@laundry.test', 'Đặng', 'Ngọc Linh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10025', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10026, '0968177562', 'user10026@laundry.test', 'Hoàng', 'Minh Anh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10026', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10027, '0989795785', 'user10027@laundry.test', 'Hoàng', 'Ngọc Hoa',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10027', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10028, '0947639437', 'user10028@laundry.test', 'Phạm', 'Thanh Phong',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10028', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10029, '0974324202', 'user10029@laundry.test', 'Đỗ', 'Minh Châu',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10029', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10030, '0921347153', 'user10030@laundry.test', 'Nguyễn', 'Ngọc Ngọc',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10030', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10031, '0986538725', 'user10031@laundry.test', 'Dương', 'Thu Tuấn',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10031', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10032, '0901348091', 'user10032@laundry.test', 'Lý', 'Gia Linh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10032', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10033, '0976660211', 'user10033@laundry.test', 'Đặng', 'Minh Sang',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10033', 'MAINTENANCE', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10034, '0945573766', 'user10034@laundry.test', 'Phan', 'Gia Sang',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10034', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10035, '0959314497', 'user10035@laundry.test', 'Võ', 'Minh Tuấn',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10035', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10036, '0985415606', 'user10036@laundry.test', 'Hồ', 'Thị Minh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10036', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10037, '0978042452', 'user10037@laundry.test', 'Nguyễn', 'Ngọc Đạt',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10037', 'MAINTENANCE', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10038, '0961325215', 'user10038@laundry.test', 'Nguyễn', 'Thị Vinh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10038', 'MAINTENANCE', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10039, '0960923927', 'user10039@laundry.test', 'Nguyễn', 'Bảo Dương',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10039', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10040, '0946040803', 'user10040@laundry.test', 'Lê', 'Văn Khánh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10040', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10041, '0984536476', 'user10041@laundry.test', 'Phan', 'Tuấn Anh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10041', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10042, '0960981816', 'user10042@laundry.test', 'Lê', 'Bảo Linh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10042', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10043, '0904513549', 'user10043@laundry.test', 'Dương', 'Ngọc Hùng',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10043', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10044, '0952607659', 'user10044@laundry.test', 'Lê', 'Thu Phong',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10044', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10045, '0924856533', 'user10045@laundry.test', 'Hoàng', 'Hữu Trang',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10045', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10046, '0902305416', 'user10046@laundry.test', 'Đỗ', 'Bảo Quang',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10046', 'MAINTENANCE', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10047, '0931524809', 'user10047@laundry.test', 'Vũ', 'Gia Vinh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10047', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10048, '0904181164', 'user10048@laundry.test', 'Huỳnh', 'Thanh Anh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10048', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10049, '0998153508', 'user10049@laundry.test', 'Phan', 'Hữu Hùng',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10049', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10050, '0915370722', 'user10050@laundry.test', 'Vũ', 'Minh Vinh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10050', 'MAINTENANCE', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10051, '0991889402', 'user10051@laundry.test', 'Huỳnh', 'Thu Đạt',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10051', 'MAINTENANCE', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10052, '0982182702', 'user10052@laundry.test', 'Bùi', 'Thị Uyên',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10052', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10053, '0965137998', 'user10053@laundry.test', 'Ngô', 'Văn Phong',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10053', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10054, '0974295686', 'user10054@laundry.test', 'Vũ', 'Minh Uyên',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10054', 'MAINTENANCE', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10055, '0992024184', 'user10055@laundry.test', 'Phan', 'Tuấn Minh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10055', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10056, '0909047747', 'user10056@laundry.test', 'Ngô', 'Minh Tuấn',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10056', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10057, '0905868465', 'user10057@laundry.test', 'Phạm', 'Gia Sang',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10057', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10058, '0916583860', 'user10058@laundry.test', 'Võ', 'Minh Oanh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10058', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10059, '0997310403', 'user10059@laundry.test', 'Võ', 'Bảo Bình',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10059', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10060, '0990341073', 'user10060@laundry.test', 'Đỗ', 'Hữu Vinh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10060', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10061, '0965457717', 'user10061@laundry.test', 'Nguyễn', 'Tuấn Hải',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10061', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10062, '0900665932', 'user10062@laundry.test', 'Dương', 'Bảo Trang',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10062', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10063, '0939094936', 'user10063@laundry.test', 'Phan', 'Minh Bình',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10063', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10064, '0917985947', 'user10064@laundry.test', 'Đỗ', 'Thu Hùng',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10064', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10065, '0976618423', 'user10065@laundry.test', 'Vũ', 'Hữu Tuấn',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10065', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10066, '0963849707', 'user10066@laundry.test', 'Phan', 'Bảo Ngọc',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10066', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10067, '0972826683', 'user10067@laundry.test', 'Lê', 'Thị Dương',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10067', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10068, '0940948744', 'user10068@laundry.test', 'Nguyễn', 'Ngọc Khánh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10068', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10069, '0967079828', 'user10069@laundry.test', 'Bùi', 'Minh Hùng',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10069', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10070, '0933250944', 'user10070@laundry.test', 'Lý', 'Tuấn Hải',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10070', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10071, '0925662833', 'user10071@laundry.test', 'Nguyễn', 'Bảo Hải',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10071', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10072, '0963427529', 'user10072@laundry.test', 'Ngô', 'Thanh Trang',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10072', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10073, '0939960010', 'user10073@laundry.test', 'Dương', 'Thanh Hoa',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10073', 'MAINTENANCE', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10074, '0988731154', 'user10074@laundry.test', 'Hồ', 'Hữu Hải',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10074', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10075, '0922185189', 'user10075@laundry.test', 'Đặng', 'Minh Oanh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10075', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10076, '0963391529', 'user10076@laundry.test', 'Võ', 'Tuấn Phong',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10076', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10077, '0971308153', 'user10077@laundry.test', 'Huỳnh', 'Hữu Phong',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10077', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10078, '0929619341', 'user10078@laundry.test', 'Đặng', 'Minh Trang',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10078', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10079, '0920585346', 'user10079@laundry.test', 'Phạm', 'Gia Anh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10079', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10080, '0935209518', 'user10080@laundry.test', 'Hồ', 'Thị Anh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10080', 'MAINTENANCE', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10081, '0991738437', 'user10081@laundry.test', 'Nguyễn', 'Gia Đạt',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10081', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10082, '0939682890', 'user10082@laundry.test', 'Hồ', 'Thanh Linh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10082', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10083, '0929819176', 'user10083@laundry.test', 'Phạm', 'Minh Dương',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10083', 'MAINTENANCE', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10084, '0946519739', 'user10084@laundry.test', 'Dương', 'Bảo Khánh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10084', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10085, '0984432005', 'user10085@laundry.test', 'Hồ', 'Văn Linh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10085', 'MAINTENANCE', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10086, '0998571325', 'user10086@laundry.test', 'Võ', 'Thị Oanh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10086', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10087, '0940140710', 'user10087@laundry.test', 'Nguyễn', 'Minh Đạt',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10087', 'MAINTENANCE', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10088, '0951936261', 'user10088@laundry.test', 'Trần', 'Hữu Tuấn',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10088', 'MAINTENANCE', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10089, '0990457437', 'user10089@laundry.test', 'Võ', 'Thu Sang',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10089', 'MAINTENANCE', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10090, '0987955159', 'user10090@laundry.test', 'Đặng', 'Bảo Minh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10090', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10091, '0921882136', 'user10091@laundry.test', 'Hoàng', 'Thanh Ngọc',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10091', 'MAINTENANCE', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10092, '0915629038', 'user10092@laundry.test', 'Lý', 'Ngọc Đạt',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10092', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10093, '0928243287', 'user10093@laundry.test', 'Đỗ', 'Thị Vinh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10093', 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10094, '0948993428', 'user10094@laundry.test', 'Trần', 'Thu Hùng',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10094', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10095, '0953245139', 'user10095@laundry.test', 'Trần', 'Thanh Trang',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10095', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10096, '0915692710', 'user10096@laundry.test', 'Phạm', 'Ngọc Đạt',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10096', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10097, '0944155580', 'user10097@laundry.test', 'Bùi', 'Thị Sang',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10097', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10098, '0938450829', 'user10098@laundry.test', 'Dương', 'Ngọc Vinh',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10098', 'MAINTENANCE', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO user_schema.user_profiles (id, phone_number, email, first_name, last_name, image_url, roles, status,
                                       created_at, updated_at)
VALUES (10099, '0984249628', 'user10099@laundry.test', 'Trần', 'Ngọc Phong',
        'https://api.dicebear.com/7.x/avataaars/svg?seed=10099', 'CUSTOMER', 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

\connect
auth_db
DELETE
FROM auth_schema.auth_accounts
WHERE id >= 10000;
DELETE
FROM auth_schema.refresh_tokens
WHERE id >= 10000;
DELETE
FROM auth_schema.email_otps
WHERE id >= 10000;
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10000, 10000, 'user10000@laundry.test', '0910000000',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10000, 10000, 'ad060890-9ce2-42ec-a1a2-8f60a443ac89', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10000, 'user10000@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10001, 10001, 'user10001@laundry.test', '0910000001',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10001, 10001, 'acf65112-e84c-4bef-9d21-568a61902a3c', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10001, 'user10001@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10002, 10002, 'user10002@laundry.test', '0910000002',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10002, 10002, '7886383c-2928-4d13-8aa6-e13e23b6d50c', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10002, 'user10002@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10003, 10003, 'user10003@laundry.test', '0910000003',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10003, 10003, '6a67694f-3147-47fd-b288-fc9ae40a2d9e', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10003, 'user10003@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10004, 10004, 'user10004@laundry.test', '0910000004',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10004, 10004, 'cdac39d3-d84e-48f6-82c8-c4373d3d3cb8', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10004, 'user10004@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10005, 10005, 'user10005@laundry.test', '0910000005',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10005, 10005, 'd8b92572-6d23-46b7-b156-476425ba38b1', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10005, 'user10005@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10006, 10006, 'user10006@laundry.test', '0910000006',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10006, 10006, 'abfcd7eb-b9f1-4889-bb1b-ec70f79ad820', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10006, 'user10006@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10007, 10007, 'user10007@laundry.test', '0910000007',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10007, 10007, '58d3ff3c-1e78-4732-b210-3bf40500d8b5', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10007, 'user10007@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10008, 10008, 'user10008@laundry.test', '0910000008',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10008, 10008, 'fe5e0ac2-9c9b-4356-b100-1b37609eb19e', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10008, 'user10008@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10009, 10009, 'user10009@laundry.test', '0910000009',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10009, 10009, '94c278f4-8fe4-4b30-bd10-6990168ff4a8', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10009, 'user10009@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10010, 10010, 'user10010@laundry.test', '0910000010',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10010, 10010, 'fec4c668-6b62-4fd2-801a-0430c3fda2d1', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10010, 'user10010@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10011, 10011, 'user10011@laundry.test', '0910000011',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10011, 10011, '6065aa34-be3d-4ac0-bd97-3993b68e64b8', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10011, 'user10011@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10012, 10012, 'user10012@laundry.test', '0910000012',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10012, 10012, 'f733a450-1914-4e31-be4c-d4399ed6edb7', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10012, 'user10012@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10013, 10013, 'user10013@laundry.test', '0910000013',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10013, 10013, '7370f788-45a1-44bf-8886-111010e13023', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10013, 'user10013@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10014, 10014, 'user10014@laundry.test', '0910000014',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10014, 10014, '99ab13ad-6f75-4a51-aa1b-3e00ee3ad421', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10014, 'user10014@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10015, 10015, 'user10015@laundry.test', '0910000015',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10015, 10015, '9c44808d-9a23-4e2e-a02f-b5c64458b95e', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10015, 'user10015@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10016, 10016, 'user10016@laundry.test', '0910000016',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10016, 10016, 'bb82ee18-6627-4358-acde-969c264b7c3f', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10016, 'user10016@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10017, 10017, 'user10017@laundry.test', '0910000017',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10017, 10017, 'e4472639-9b05-4bb9-8408-16d25a5407bc', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10017, 'user10017@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10018, 10018, 'user10018@laundry.test', '0910000018',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10018, 10018, '1a57869b-f1f4-4b2b-a6f6-2cdd1535564e', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10018, 'user10018@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10019, 10019, 'user10019@laundry.test', '0910000019',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10019, 10019, '1f3f69a3-f3b4-4283-a23f-e93b7b8322ac', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10019, 'user10019@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10020, 10020, 'user10020@laundry.test', '0910000020',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10020, 10020, 'ef4db2ae-c559-4b67-946d-8d7e78947790', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10020, 'user10020@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10021, 10021, 'user10021@laundry.test', '0910000021',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10021, 10021, '9a839d44-3b56-447c-8128-6bef9013ba64', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10021, 'user10021@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10022, 10022, 'user10022@laundry.test', '0910000022',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10022, 10022, 'd806c7b4-467c-43e0-baf0-444260b17ae5', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10022, 'user10022@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10023, 10023, 'user10023@laundry.test', '0910000023',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10023, 10023, 'a60738ce-0fac-4746-9667-f25f2281ccda', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10023, 'user10023@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10024, 10024, 'user10024@laundry.test', '0910000024',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10024, 10024, '12095b94-135a-4da8-99fc-2fb5f6461f0f', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10024, 'user10024@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10025, 10025, 'user10025@laundry.test', '0910000025',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10025, 10025, 'c9fb7eb3-179d-42d6-a0cd-fa7ae78336c1', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10025, 'user10025@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10026, 10026, 'user10026@laundry.test', '0910000026',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10026, 10026, 'ab9a4d80-1d19-424f-a785-e310c11baf94', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10026, 'user10026@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10027, 10027, 'user10027@laundry.test', '0910000027',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10027, 10027, '32453650-813e-4588-8668-e9ccb4d42992', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10027, 'user10027@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10028, 10028, 'user10028@laundry.test', '0910000028',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10028, 10028, '1fe8041d-1225-4d2b-a4b8-2454cf593bab', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10028, 'user10028@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10029, 10029, 'user10029@laundry.test', '0910000029',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10029, 10029, '220bda57-0243-4517-8a0c-56f0b021c94e', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10029, 'user10029@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10030, 10030, 'user10030@laundry.test', '0910000030',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10030, 10030, '9310b2b3-d44f-4a68-9f33-5bbd7b203dda', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10030, 'user10030@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10031, 10031, 'user10031@laundry.test', '0910000031',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10031, 10031, '28da15b3-e41a-4da0-bb18-2e5c1830efa1', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10031, 'user10031@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10032, 10032, 'user10032@laundry.test', '0910000032',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10032, 10032, 'f3f41c04-1947-4b65-a5ae-82974faa8c9c', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10032, 'user10032@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10033, 10033, 'user10033@laundry.test', '0910000033',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10033, 10033, '6a0a865d-0d99-4b2f-94e9-a2be307adb47', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10033, 'user10033@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10034, 10034, 'user10034@laundry.test', '0910000034',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10034, 10034, '2d424fa9-4e7f-4af2-855a-6c164e392166', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10034, 'user10034@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10035, 10035, 'user10035@laundry.test', '0910000035',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10035, 10035, 'ffb66f53-918b-40c2-ba6f-fdb68bb4c4e0', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10035, 'user10035@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10036, 10036, 'user10036@laundry.test', '0910000036',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10036, 10036, '41004fe0-396e-40de-a46d-0edf028506ae', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10036, 'user10036@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10037, 10037, 'user10037@laundry.test', '0910000037',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10037, 10037, '1fbf1dc5-2c06-4735-924d-3927ca6d9a43', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10037, 'user10037@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10038, 10038, 'user10038@laundry.test', '0910000038',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10038, 10038, 'fcdbdf76-5b9b-4fd0-a4b4-7c083e319014', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10038, 'user10038@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10039, 10039, 'user10039@laundry.test', '0910000039',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10039, 10039, 'f38c3430-35f9-4a94-89e8-cefb1b8c9865', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10039, 'user10039@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10040, 10040, 'user10040@laundry.test', '0910000040',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10040, 10040, 'd67f3c84-1a28-4835-a9b1-246d7a59d0fc', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10040, 'user10040@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10041, 10041, 'user10041@laundry.test', '0910000041',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10041, 10041, 'e0ba8394-4c32-43ad-b45e-3c4c958dd219', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10041, 'user10041@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10042, 10042, 'user10042@laundry.test', '0910000042',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10042, 10042, '0c549ceb-f60a-45be-a712-86e580b7a840', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10042, 'user10042@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10043, 10043, 'user10043@laundry.test', '0910000043',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10043, 10043, 'c3d8991f-f662-4cd3-8e54-dfd4c2abd756', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10043, 'user10043@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10044, 10044, 'user10044@laundry.test', '0910000044',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10044, 10044, '812203cf-db0f-4cc5-b313-9c1e2df829c4', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10044, 'user10044@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10045, 10045, 'user10045@laundry.test', '0910000045',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10045, 10045, '8284fffc-970e-4fd0-b972-3edff4612f24', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10045, 'user10045@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10046, 10046, 'user10046@laundry.test', '0910000046',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10046, 10046, 'eccf647d-fe77-4645-ba40-fc8f951f81c7', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10046, 'user10046@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10047, 10047, 'user10047@laundry.test', '0910000047',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10047, 10047, '29ef61ed-6ff4-4023-8c7a-093699979ebd', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10047, 'user10047@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10048, 10048, 'user10048@laundry.test', '0910000048',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10048, 10048, '1865b823-33bc-4fbc-9c8a-c7e07e31e9be', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10048, 'user10048@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10049, 10049, 'user10049@laundry.test', '0910000049',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10049, 10049, '924b899d-fac3-4779-8075-3978b698f982', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10049, 'user10049@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10050, 10050, 'user10050@laundry.test', '0910000050',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10050, 10050, '6e375135-f559-41f9-aa74-1b898c7b7924', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10050, 'user10050@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10051, 10051, 'user10051@laundry.test', '0910000051',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10051, 10051, '0fa31714-4a49-433f-bbd4-e3bf48e20ec1', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10051, 'user10051@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10052, 10052, 'user10052@laundry.test', '0910000052',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10052, 10052, '21870a1c-e735-42f3-ad8b-2cde351f54e2', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10052, 'user10052@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10053, 10053, 'user10053@laundry.test', '0910000053',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10053, 10053, 'c7b53e6e-05ed-43bc-813c-e34f364e6a86', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10053, 'user10053@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10054, 10054, 'user10054@laundry.test', '0910000054',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10054, 10054, '79587077-5cde-4dc7-b843-4e72acb7c769', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10054, 'user10054@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10055, 10055, 'user10055@laundry.test', '0910000055',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10055, 10055, '7506b3c4-ab81-4ade-8ab0-50128d1fecb0', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10055, 'user10055@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10056, 10056, 'user10056@laundry.test', '0910000056',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10056, 10056, '116f059c-d5d4-4c1d-86ee-c12bdb9cb826', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10056, 'user10056@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10057, 10057, 'user10057@laundry.test', '0910000057',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10057, 10057, 'f799f3a2-03da-4a47-b98c-6155e21c0db4', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10057, 'user10057@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10058, 10058, 'user10058@laundry.test', '0910000058',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10058, 10058, 'bfb0e214-a0a7-464e-915e-ba1857d7b818', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10058, 'user10058@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10059, 10059, 'user10059@laundry.test', '0910000059',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10059, 10059, 'ed2a1bc8-946e-480c-ba28-54ca4f43d1a4', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10059, 'user10059@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10060, 10060, 'user10060@laundry.test', '0910000060',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10060, 10060, '25a8e0ce-c9a1-4af0-9dd9-458f14e91328', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10060, 'user10060@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10061, 10061, 'user10061@laundry.test', '0910000061',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10061, 10061, '725c9a78-d69a-4fe4-ae28-e55376c6e245', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10061, 'user10061@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10062, 10062, 'user10062@laundry.test', '0910000062',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10062, 10062, 'd3e3d098-5f41-417a-982c-1191ea6618ca', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10062, 'user10062@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10063, 10063, 'user10063@laundry.test', '0910000063',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10063, 10063, '2c029e9e-d057-4bb4-9c93-577e89025592', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10063, 'user10063@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10064, 10064, 'user10064@laundry.test', '0910000064',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10064, 10064, '5f2090a4-aa9c-42e3-8294-6aed2dfa6e4a', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10064, 'user10064@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10065, 10065, 'user10065@laundry.test', '0910000065',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10065, 10065, 'f32ce74a-eca6-462f-904a-ebb90de60d80', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10065, 'user10065@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10066, 10066, 'user10066@laundry.test', '0910000066',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10066, 10066, '96c0cbf2-8ece-4959-bef9-554c8a9d9d18', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10066, 'user10066@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10067, 10067, 'user10067@laundry.test', '0910000067',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10067, 10067, 'c64aea5d-93b5-4685-a8ee-fe107a279c9e', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10067, 'user10067@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10068, 10068, 'user10068@laundry.test', '0910000068',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10068, 10068, '53b7f285-a2cd-4cc5-8b03-787d86cbb71e', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10068, 'user10068@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10069, 10069, 'user10069@laundry.test', '0910000069',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10069, 10069, '77139562-d070-406d-ae9f-6e07a6eae7fa', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10069, 'user10069@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10070, 10070, 'user10070@laundry.test', '0910000070',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10070, 10070, 'e6d8e405-b574-4f50-a9f2-c056a8da30e0', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10070, 'user10070@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10071, 10071, 'user10071@laundry.test', '0910000071',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10071, 10071, 'f91826f0-80f4-44e3-9c05-f052a69c1501', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10071, 'user10071@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10072, 10072, 'user10072@laundry.test', '0910000072',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10072, 10072, '9b61d4fe-997a-4e7f-9bef-0b11dfd666f5', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10072, 'user10072@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10073, 10073, 'user10073@laundry.test', '0910000073',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10073, 10073, '130a263a-e121-459d-b939-1d2f9d7ddabb', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10073, 'user10073@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10074, 10074, 'user10074@laundry.test', '0910000074',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10074, 10074, '0628c453-0365-492c-844e-d681d83169f3', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10074, 'user10074@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10075, 10075, 'user10075@laundry.test', '0910000075',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10075, 10075, '87cffdcf-75f7-4e68-8bd8-627a197f1fd3', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10075, 'user10075@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10076, 10076, 'user10076@laundry.test', '0910000076',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10076, 10076, '9ddb8e20-42ba-46d9-9c37-0cc0ecab8e9d', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10076, 'user10076@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10077, 10077, 'user10077@laundry.test', '0910000077',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10077, 10077, '1caf82c1-66cc-45db-bd03-05cadddda428', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10077, 'user10077@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10078, 10078, 'user10078@laundry.test', '0910000078',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10078, 10078, '105bf761-f111-4068-a680-f1c72ec48de6', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10078, 'user10078@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10079, 10079, 'user10079@laundry.test', '0910000079',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10079, 10079, '54b03242-cce3-4db3-a917-da0839fda0fc', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10079, 'user10079@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10080, 10080, 'user10080@laundry.test', '0910000080',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10080, 10080, 'eada20c1-0f09-483e-895e-260117ad6828', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10080, 'user10080@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10081, 10081, 'user10081@laundry.test', '0910000081',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10081, 10081, 'c00dc213-eac1-4d6d-be15-d2cc71939c5d', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10081, 'user10081@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10082, 10082, 'user10082@laundry.test', '0910000082',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10082, 10082, '0f78ef8e-48df-4ad0-aca6-7f5f5b706928', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10082, 'user10082@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10083, 10083, 'user10083@laundry.test', '0910000083',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10083, 10083, '63d7f462-3a9c-4de2-8a9c-9695603f1167', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10083, 'user10083@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10084, 10084, 'user10084@laundry.test', '0910000084',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10084, 10084, '4cbad858-e5a3-40da-baef-81067e1e882c', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10084, 'user10084@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10085, 10085, 'user10085@laundry.test', '0910000085',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10085, 10085, '8ca4c0c2-1ab1-48f5-9adb-bda979c9e98f', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10085, 'user10085@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10086, 10086, 'user10086@laundry.test', '0910000086',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10086, 10086, 'e4ff0b09-0d34-4e15-a793-ada02421fb59', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10086, 'user10086@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10087, 10087, 'user10087@laundry.test', '0910000087',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10087, 10087, 'd56de066-e3b0-480c-91bd-26febd338464', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10087, 'user10087@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10088, 10088, 'user10088@laundry.test', '0910000088',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10088, 10088, '7f4ed8f0-9c5b-4d6b-96e2-d675a386e145', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10088, 'user10088@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10089, 10089, 'user10089@laundry.test', '0910000089',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10089, 10089, '7250351a-a1d8-4432-afa7-4c8eae325c97', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10089, 'user10089@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10090, 10090, 'user10090@laundry.test', '0910000090',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10090, 10090, '9d92f092-b8e5-41b4-a8cd-e9aa8eea9a09', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10090, 'user10090@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10091, 10091, 'user10091@laundry.test', '0910000091',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10091, 10091, 'f8413779-efd0-4f23-84f7-658c40e840df', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10091, 'user10091@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10092, 10092, 'user10092@laundry.test', '0910000092',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10092, 10092, '09a7537e-149d-4f32-9b94-b3e1672f6cbb', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10092, 'user10092@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10093, 10093, 'user10093@laundry.test', '0910000093',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10093, 10093, 'f3bd319b-fb2b-4bd0-b120-070ab3e73ae3', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10093, 'user10093@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10094, 10094, 'user10094@laundry.test', '0910000094',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10094, 10094, 'f5ae32a8-699a-4409-a856-bec715d35b4b', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10094, 'user10094@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10095, 10095, 'user10095@laundry.test', '0910000095',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10095, 10095, 'a4594c72-1825-48de-a4ca-93dd390803c9', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10095, 'user10095@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10096, 10096, 'user10096@laundry.test', '0910000096',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10096, 10096, 'cb4afc6b-ef39-44d7-b8ea-27cb4388cb53', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10096, 'user10096@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10097, 10097, 'user10097@laundry.test', '0910000097',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10097, 10097, '3db7d259-cbdc-4ce1-8821-b64e2ed58302', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10097, 'user10097@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10098, 10098, 'user10098@laundry.test', '0910000098',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10098, 10098, '7488d957-adcd-41ef-a3d5-bfbf8d052909', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10098, 'user10098@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.auth_accounts (id, user_id, email, phone_number, password_hash, auth_provider, email_verified,
                                       phone_verified, status, created_at, updated_at)
VALUES (10099, 10099, 'user10099@laundry.test', '0910000099',
        '$2a$10$w/X2B3W3xM/Pz4V1p7vR8e8k9N0o1P2q3r4s5t6u7v8w9x0y1z', 'LOCAL', true, true, 'ACTIVE', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.refresh_tokens (id, account_id, token_hash, expires_at, created_at)
VALUES (10099, 10099, '2a2ff0db-6879-43e0-b57a-e5a48d0a8c78', CURRENT_TIMESTAMP + INTERVAL '30 days',
        CURRENT_TIMESTAMP);
INSERT INTO auth_schema.email_otps (id, email, otp_hash, purpose, expires_at, created_at)
VALUES (10099, 'user10099@laundry.test', 'hash', 'VERIFY_EMAIL', CURRENT_TIMESTAMP + INTERVAL '5 minutes',
        CURRENT_TIMESTAMP);

\connect
store_db
DELETE
FROM store_schema.stores
WHERE id >= 10000;
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10000, 'Smart Locker Lê Lợi 0', '707 Nguyễn Đình Chiểu, Quận 1, Cần Thơ', 10.797574361516624,
        106.62940258732446, '0976050458', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10001, 'Smart Locker Nguyễn Huệ 1', '230 Trần Hưng Đạo, Quận 1, Hà Nội', 10.807150040531544, 106.69204088877217,
        '0903656967', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10002, 'Smart Locker Hai Bà Trưng 2', '848 Trần Hưng Đạo, Quận 7, Hải Phòng', 10.735220791176808,
        106.67749210763164, '0970184331', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10003, 'Smart Locker Lê Duẩn 3', '846 Nguyễn Đình Chiểu, Quận 11, Hải Phòng', 10.724029270839551,
        106.67159804069988, '0914246209', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10004, 'Smart Locker Hàm Nghi 4', '564 Lê Duẩn, Quận 7, Cần Thơ', 10.78596695139686, 106.62419222595992,
        '0955120230', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10005, 'Smart Locker Hai Bà Trưng 5', '722 Hai Bà Trưng, Quận 12, Hải Phòng', 10.738701831213946,
        106.63552788841463, '0913965183', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10006, 'Smart Locker Nguyễn Đình Chiểu 6', '380 Lý Tự Trọng, Quận 4, Hải Phòng', 10.798349003628994,
        106.61803773380457, '0916668671', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10007, 'Smart Locker Lê Duẩn 7', '155 Lê Duẩn, Quận 2, Đà Nẵng', 10.74858482761567, 106.69585152849211,
        '0908944957', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10008, 'Smart Locker Lê Lợi 8', '279 Nguyễn Huệ, Quận 6, Cần Thơ', 10.763196151657898, 106.69568773246954,
        '0935935619', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10009, 'Smart Locker Trần Hưng Đạo 9', '997 Nguyễn Huệ, Quận 5, Cần Thơ', 10.804107947530634,
        106.63942077297625, '0918411551', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10010, 'Smart Locker Nguyễn Huệ 10', '158 Lý Tự Trọng, Quận 8, Đà Nẵng', 10.778963796409128, 106.6270228266883,
        '0946034277', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10011, 'Smart Locker Hàm Nghi 11', '449 Lê Lợi, Quận 9, Hải Phòng', 10.752302265322367, 106.66093900651784,
        '0900086536', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10012, 'Smart Locker Hàm Nghi 12', '49 Nguyễn Đình Chiểu, Quận 1, Hồ Chí Minh', 10.729362896942524,
        106.65914496969253, '0964454683', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10013, 'Smart Locker Phạm Ngũ Lão 13', '107 Trần Hưng Đạo, Quận 9, Hồ Chí Minh', 10.73832882372995,
        106.65731894909592, '0913102044', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10014, 'Smart Locker Phạm Ngũ Lão 14', '944 Pasteur, Quận 8, Hà Nội', 10.786552771381192, 106.70380681721427,
        '0962371998', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10015, 'Smart Locker Phạm Ngũ Lão 15', '47 Hai Bà Trưng, Quận 9, Hà Nội', 10.725754117914498,
        106.67981775490269, '0948917478', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10016, 'Smart Locker Trần Hưng Đạo 16', '33 Hai Bà Trưng, Quận 10, Hồ Chí Minh', 10.80376406968898,
        106.69141881066692, '0969070889', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10017, 'Smart Locker Lê Lợi 17', '863 Lê Duẩn, Quận 2, Hồ Chí Minh', 10.71556195537206, 106.66906242607506,
        '0933063316', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10018, 'Smart Locker Lê Lợi 18', '982 Pasteur, Quận 3, Hải Phòng', 10.712649666886575, 106.64700694743186,
        '0930865235', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10019, 'Smart Locker Lý Tự Trọng 19', '453 Hai Bà Trưng, Quận 4, Đà Nẵng', 10.721439625446504,
        106.63922950555063, '0929069633', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10020, 'Smart Locker Pasteur 20', '460 Pasteur, Quận 2, Đà Nẵng', 10.727009665895602, 106.66413134746665,
        '0916457611', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10021, 'Smart Locker Lê Lợi 21', '145 Lê Duẩn, Quận 1, Đà Nẵng', 10.770058633687476, 106.64499390761098,
        '0957760784', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10022, 'Smart Locker Lê Duẩn 22', '524 Nguyễn Huệ, Quận 6, Cần Thơ', 10.718178784825476, 106.68503038627072,
        '0996288755', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10023, 'Smart Locker Nguyễn Đình Chiểu 23', '97 Pasteur, Quận 8, Đà Nẵng', 10.718681130055716,
        106.6737720206077, '0935190060', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10024, 'Smart Locker Lê Duẩn 24', '425 Nguyễn Huệ, Quận 9, Đà Nẵng', 10.747090004864639, 106.69819089355086,
        '0970855864', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10025, 'Smart Locker Hai Bà Trưng 25', '751 Phạm Ngũ Lão, Quận 7, Hồ Chí Minh', 10.790532229300462,
        106.6392637573168, '0992782924', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10026, 'Smart Locker Phạm Ngũ Lão 26', '3 Hai Bà Trưng, Quận 9, Hồ Chí Minh', 10.74899987182812,
        106.70101023078985, '0988338552', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10027, 'Smart Locker Lê Duẩn 27', '61 Lê Lợi, Quận 2, Hải Phòng', 10.808219034430484, 106.692713700266,
        '0998903692', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10028, 'Smart Locker Trần Hưng Đạo 28', '559 Trần Hưng Đạo, Quận 6, Đà Nẵng', 10.80506279311897,
        106.65148704497769, '0966464879', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10029, 'Smart Locker Lê Duẩn 29', '735 Hai Bà Trưng, Quận 12, Hồ Chí Minh', 10.73838185934542,
        106.61518138673755, '0915753416', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10030, 'Smart Locker Nguyễn Đình Chiểu 30', '578 Pasteur, Quận 4, Hà Nội', 10.74817677120606,
        106.63378708191942, '0961882714', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10031, 'Smart Locker Trần Hưng Đạo 31', '909 Lê Duẩn, Quận 8, Đà Nẵng', 10.748099356490894, 106.64375613247606,
        '0994605831', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10032, 'Smart Locker Nguyễn Huệ 32', '119 Hàm Nghi, Quận 4, Hải Phòng', 10.74927538246301, 106.70241572778441,
        '0965591767', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10033, 'Smart Locker Lý Tự Trọng 33', '413 Lê Lợi, Quận 11, Đà Nẵng', 10.759188825742273, 106.69659971527686,
        '0979841574', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10034, 'Smart Locker Lê Lợi 34', '591 Hai Bà Trưng, Quận 3, Hồ Chí Minh', 10.726028123370645,
        106.62587361805565, '0947059673', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10035, 'Smart Locker Trần Hưng Đạo 35', '40 Phạm Ngũ Lão, Quận 6, Hồ Chí Minh', 10.802934886262264,
        106.67719669663707, '0904680220', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10036, 'Smart Locker Lý Tự Trọng 36', '687 Nguyễn Huệ, Quận 6, Cần Thơ', 10.805041001710201, 106.66581931056768,
        '0979542778', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10037, 'Smart Locker Trần Hưng Đạo 37', '236 Lê Duẩn, Quận 10, Hà Nội', 10.715062519174934, 106.69260250221659,
        '0993767522', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10038, 'Smart Locker Hàm Nghi 38', '150 Nguyễn Đình Chiểu, Quận 3, Đà Nẵng', 10.762159132272624,
        106.6493416533751, '0943638728', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10039, 'Smart Locker Nguyễn Đình Chiểu 39', '333 Nguyễn Huệ, Quận 6, Đà Nẵng', 10.73971063541474,
        106.70396866937128, '0967175905', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10040, 'Smart Locker Hai Bà Trưng 40', '894 Phạm Ngũ Lão, Quận 9, Hải Phòng', 10.779427448165459,
        106.6295867864145, '0935022019', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10041, 'Smart Locker Pasteur 41', '967 Hai Bà Trưng, Quận 4, Hải Phòng', 10.807838533549218, 106.6514363148579,
        '0998694453', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10042, 'Smart Locker Phạm Ngũ Lão 42', '147 Lê Lợi, Quận 12, Hồ Chí Minh', 10.730491705369316,
        106.70320349758428, '0963780918', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10043, 'Smart Locker Hai Bà Trưng 43', '260 Lê Lợi, Quận 6, Hải Phòng', 10.774500259285517, 106.6991070613983,
        '0984847340', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10044, 'Smart Locker Pasteur 44', '446 Lê Duẩn, Quận 4, Hải Phòng', 10.747568323431773, 106.64949014789738,
        '0923603234', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10045, 'Smart Locker Nguyễn Huệ 45', '995 Nguyễn Đình Chiểu, Quận 3, Hà Nội', 10.769412901567355,
        106.6942023457773, '0942568838', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10046, 'Smart Locker Lý Tự Trọng 46', '618 Hai Bà Trưng, Quận 7, Hải Phòng', 10.754452247191793,
        106.65589541131122, '0916676862', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10047, 'Smart Locker Trần Hưng Đạo 47', '639 Phạm Ngũ Lão, Quận 6, Cần Thơ', 10.777873424527298,
        106.67354641152959, '0987755140', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10048, 'Smart Locker Lê Lợi 48', '238 Nguyễn Huệ, Quận 5, Hải Phòng', 10.781406393349085, 106.66515369561968,
        '0923974438', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10049, 'Smart Locker Phạm Ngũ Lão 49', '592 Nguyễn Huệ, Quận 1, Hải Phòng', 10.791447344588217,
        106.6729669470502, '0933385995', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10050, 'Smart Locker Trần Hưng Đạo 50', '568 Trần Hưng Đạo, Quận 1, Hồ Chí Minh', 10.714672757857832,
        106.62263632427542, '0960707255', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10051, 'Smart Locker Lý Tự Trọng 51', '13 Hàm Nghi, Quận 1, Hà Nội', 10.722918737376006, 106.64157334895593,
        '0950726436', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10052, 'Smart Locker Pasteur 52', '927 Lê Duẩn, Quận 3, Cần Thơ', 10.761963180817226, 106.63215051933248,
        '0947164080', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10053, 'Smart Locker Pasteur 53', '366 Lê Lợi, Quận 5, Cần Thơ', 10.755412914205127, 106.65947486394039,
        '0954545339', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10054, 'Smart Locker Lý Tự Trọng 54', '786 Lê Lợi, Quận 10, Hà Nội', 10.803032617940714, 106.61074948878507,
        '0904937360', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10055, 'Smart Locker Nguyễn Đình Chiểu 55', '545 Lê Lợi, Quận 11, Đà Nẵng', 10.754575954834468,
        106.61181602754615, '0995541108', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10056, 'Smart Locker Lê Lợi 56', '143 Nguyễn Huệ, Quận 9, Hồ Chí Minh', 10.717780005480853, 106.65647369210991,
        '0955120778', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10057, 'Smart Locker Nguyễn Huệ 57', '139 Lê Lợi, Quận 12, Hải Phòng', 10.7870250633197, 106.63862332108638,
        '0919694452', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10058, 'Smart Locker Phạm Ngũ Lão 58', '232 Pasteur, Quận 10, Hồ Chí Minh', 10.801390090870678,
        106.67690675049192, '0917186767', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10059, 'Smart Locker Pasteur 59', '347 Trần Hưng Đạo, Quận 2, Hồ Chí Minh', 10.811421212950266,
        106.68327833984098, '0953006749', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10060, 'Smart Locker Phạm Ngũ Lão 60', '513 Lý Tự Trọng, Quận 6, Đà Nẵng', 10.747383335626694,
        106.67229751538736, '0925016862', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10061, 'Smart Locker Trần Hưng Đạo 61', '698 Hàm Nghi, Quận 4, Hồ Chí Minh', 10.739540494239227,
        106.69621164901153, '0957934895', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10062, 'Smart Locker Trần Hưng Đạo 62', '838 Phạm Ngũ Lão, Quận 4, Hải Phòng', 10.761265286741049,
        106.65681825238639, '0917376316', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10063, 'Smart Locker Lê Lợi 63', '871 Phạm Ngũ Lão, Quận 10, Hải Phòng', 10.789522266680098, 106.62730811039245,
        '0962056966', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10064, 'Smart Locker Lý Tự Trọng 64', '260 Pasteur, Quận 6, Cần Thơ', 10.7869341675492, 106.61524733650299,
        '0974125245', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10065, 'Smart Locker Nguyễn Huệ 65', '91 Trần Hưng Đạo, Quận 10, Đà Nẵng', 10.751403709320044,
        106.69662614696983, '0945582609', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10066, 'Smart Locker Lê Lợi 66', '831 Lê Duẩn, Quận 6, Hà Nội', 10.793680502933926, 106.65180198693382,
        '0929691868', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10067, 'Smart Locker Nguyễn Huệ 67', '462 Lý Tự Trọng, Quận 2, Cần Thơ', 10.802270097746613, 106.61898489403991,
        '0942278425', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10068, 'Smart Locker Lý Tự Trọng 68', '57 Lê Duẩn, Quận 7, Hải Phòng', 10.784209379814117, 106.63257109320149,
        '0991701849', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10069, 'Smart Locker Lê Duẩn 69', '341 Hai Bà Trưng, Quận 6, Hải Phòng', 10.7912751297514, 106.65824961769715,
        '0921664087', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10070, 'Smart Locker Trần Hưng Đạo 70', '746 Lý Tự Trọng, Quận 10, Hà Nội', 10.759336459807038,
        106.656286113972, '0934161675', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10071, 'Smart Locker Hàm Nghi 71', '348 Trần Hưng Đạo, Quận 10, Hải Phòng', 10.797149201481036,
        106.70876403087684, '0911270472', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10072, 'Smart Locker Nguyễn Đình Chiểu 72', '201 Lý Tự Trọng, Quận 5, Hà Nội', 10.715749850854925,
        106.61601697892124, '0961178148', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10073, 'Smart Locker Nguyễn Đình Chiểu 73', '174 Pasteur, Quận 10, Hồ Chí Minh', 10.747744605971498,
        106.65411030909677, '0903396214', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10074, 'Smart Locker Nguyễn Đình Chiểu 74', '605 Lê Lợi, Quận 11, Hồ Chí Minh', 10.778631129576931,
        106.65987926604423, '0949486500', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10075, 'Smart Locker Lê Lợi 75', '672 Hàm Nghi, Quận 12, Cần Thơ', 10.796677032179108, 106.63972556771961,
        '0942534023', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10076, 'Smart Locker Nguyễn Đình Chiểu 76', '696 Nguyễn Huệ, Quận 11, Hải Phòng', 10.74037762539476,
        106.68768069873626, '0962263834', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10077, 'Smart Locker Nguyễn Huệ 77', '924 Pasteur, Quận 2, Hà Nội', 10.810747179708622, 106.65986386448523,
        '0911090081', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10078, 'Smart Locker Phạm Ngũ Lão 78', '630 Lý Tự Trọng, Quận 12, Hải Phòng', 10.730549037524671,
        106.68452363226407, '0991547446', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10079, 'Smart Locker Lý Tự Trọng 79', '279 Hàm Nghi, Quận 8, Hồ Chí Minh', 10.759487840852815,
        106.68763583555058, '0992459267', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10080, 'Smart Locker Nguyễn Huệ 80', '516 Lê Lợi, Quận 11, Hồ Chí Minh', 10.764956714808884, 106.68065428695753,
        '0991911299', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10081, 'Smart Locker Phạm Ngũ Lão 81', '26 Pasteur, Quận 11, Cần Thơ', 10.76657720010251, 106.612789570639,
        '0928635653', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10082, 'Smart Locker Nguyễn Huệ 82', '51 Pasteur, Quận 3, Hồ Chí Minh', 10.795359783696604, 106.6269470437909,
        '0958100495', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10083, 'Smart Locker Trần Hưng Đạo 83', '795 Nguyễn Huệ, Quận 8, Đà Nẵng', 10.758166633294046,
        106.67427989752386, '0935741405', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10084, 'Smart Locker Trần Hưng Đạo 84', '123 Lê Lợi, Quận 3, Đà Nẵng', 10.75992230661765, 106.62426692860959,
        '0945195447', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10085, 'Smart Locker Pasteur 85', '282 Nguyễn Huệ, Quận 5, Hải Phòng', 10.745926440943947, 106.66853416922297,
        '0929537989', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10086, 'Smart Locker Phạm Ngũ Lão 86', '777 Trần Hưng Đạo, Quận 1, Đà Nẵng', 10.801033420173189,
        106.64507668204446, '0963059640', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10087, 'Smart Locker Lê Duẩn 87', '529 Phạm Ngũ Lão, Quận 1, Cần Thơ', 10.728841645000005, 106.69277098295112,
        '0987642226', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10088, 'Smart Locker Nguyễn Đình Chiểu 88', '739 Nguyễn Đình Chiểu, Quận 6, Hải Phòng', 10.772462428211178,
        106.61272437135352, '0963492780', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10089, 'Smart Locker Lê Lợi 89', '611 Lê Duẩn, Quận 3, Cần Thơ', 10.754245184797673, 106.64249750486375,
        '0931981250', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10090, 'Smart Locker Hàm Nghi 90', '270 Hai Bà Trưng, Quận 4, Hải Phòng', 10.781664007617165, 106.6218148973026,
        '0902859852', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10091, 'Smart Locker Lê Lợi 91', '742 Trần Hưng Đạo, Quận 6, Đà Nẵng', 10.715335888667575, 106.68643114966898,
        '0935269485', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10092, 'Smart Locker Phạm Ngũ Lão 92', '446 Nguyễn Huệ, Quận 10, Hà Nội', 10.807139739210776,
        106.65566767105541, '0979810027', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10093, 'Smart Locker Lê Lợi 93', '10 Hai Bà Trưng, Quận 11, Cần Thơ', 10.72665309435018, 106.62379114154194,
        '0952774549', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10094, 'Smart Locker Nguyễn Huệ 94', '441 Phạm Ngũ Lão, Quận 1, Hồ Chí Minh', 10.749821708481493,
        106.69479727348721, '0908402526', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10095, 'Smart Locker Lý Tự Trọng 95', '344 Phạm Ngũ Lão, Quận 11, Hà Nội', 10.756746116473394,
        106.6773318760713, '0990228781', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10096, 'Smart Locker Lê Duẩn 96', '903 Lê Duẩn, Quận 1, Cần Thơ', 10.781186982688242, 106.6570189461195,
        '0921272636', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10097, 'Smart Locker Lê Lợi 97', '854 Lý Tự Trọng, Quận 12, Cần Thơ', 10.769562512988864, 106.63812883281504,
        '0971119581', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10098, 'Smart Locker Nguyễn Đình Chiểu 98', '263 Nguyễn Đình Chiểu, Quận 3, Cần Thơ', 10.770382045155397,
        106.63004769955077, '0984537155', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO store_schema.stores (id, name, address, latitude, longitude, contact_phone, status, created_at, updated_at)
VALUES (10099, 'Smart Locker Lê Duẩn 99', '134 Lý Tự Trọng, Quận 9, Cần Thơ', 10.751525270702437, 106.6697865657926,
        '0985143074', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

\connect
locker_db
DELETE
FROM locker_schema.lockers
WHERE id >= 10000;
DELETE
FROM locker_schema.locker_boxes
WHERE id >= 10000;
DELETE
FROM locker_schema.locker_reports
WHERE id >= 10000;
DELETE
FROM locker_schema.maintenance_schedules
WHERE id >= 10000;
DELETE
FROM locker_schema.repair_logs
WHERE id >= 10000;
DELETE
FROM locker_schema.locker_report_ratings
WHERE id >= 10000;
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10000, 10000, 'CAB-PROD-1000', 'Tủ tự động 0', 'ACTIVE', '776 Hàm Nghi', 10.726486211693993, 106.62837876061072,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10000, 10000, 1, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10000, 10000, 10019, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10000, 10000, 10019, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10000, 10000, 'Bảo trì định kỳ 0', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10000, 10000, 10019, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10001, 10001, 'CAB-PROD-1001', 'Tủ tự động 1', 'ACTIVE', '907 Nguyễn Huệ', 10.743761812609925, 106.646817883229,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10001, 10001, 2, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10001, 10001, 10006, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10001, 10001, 10006, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10001, 10001, 'Bảo trì định kỳ 1', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10001, 10001, 10006, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10002, 10002, 'CAB-PROD-1002', 'Tủ tự động 2', 'ACTIVE', '409 Nguyễn Đình Chiểu', 10.745435673543435,
        106.65190452631539, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10002, 10002, 3, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10002, 10002, 10038, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10002, 10002, 10038, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10002, 10002, 'Bảo trì định kỳ 2', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10002, 10002, 10038, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10003, 10003, 'CAB-PROD-1003', 'Tủ tự động 3', 'ACTIVE', '563 Phạm Ngũ Lão', 10.718310098728589,
        106.67603932267465, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10003, 10003, 4, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10003, 10003, 10052, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10003, 10003, 10052, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10003, 10003, 'Bảo trì định kỳ 3', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10003, 10003, 10052, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10004, 10004, 'CAB-PROD-1004', 'Tủ tự động 4', 'ACTIVE', '749 Hai Bà Trưng', 10.74240703008596,
        106.65163638508399, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10004, 10004, 5, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10004, 10004, 10030, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10004, 10004, 10030, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10004, 10004, 'Bảo trì định kỳ 4', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10004, 10004, 10030, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10005, 10005, 'CAB-PROD-1005', 'Tủ tự động 5', 'ACTIVE', '650 Phạm Ngũ Lão', 10.785091330372097,
        106.66408921456716, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10005, 10005, 6, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10005, 10005, 10035, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10005, 10005, 10035, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10005, 10005, 'Bảo trì định kỳ 5', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10005, 10005, 10035, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10006, 10006, 'CAB-PROD-1006', 'Tủ tự động 6', 'ACTIVE', '418 Hàm Nghi', 10.788983563755194, 106.69284442397556,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10006, 10006, 7, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10006, 10006, 10035, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10006, 10006, 10035, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10006, 10006, 'Bảo trì định kỳ 6', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10006, 10006, 10035, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10007, 10007, 'CAB-PROD-1007', 'Tủ tự động 7', 'ACTIVE', '207 Hai Bà Trưng', 10.808880939239083,
        106.61554150271749, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10007, 10007, 8, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10007, 10007, 10059, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10007, 10007, 10059, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10007, 10007, 'Bảo trì định kỳ 7', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10007, 10007, 10059, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10008, 10008, 'CAB-PROD-1008', 'Tủ tự động 8', 'ACTIVE', '322 Hàm Nghi', 10.736733213173572, 106.70559877950699,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10008, 10008, 9, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10008, 10008, 10079, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10008, 10008, 10079, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10008, 10008, 'Bảo trì định kỳ 8', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10008, 10008, 10079, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10009, 10009, 'CAB-PROD-1009', 'Tủ tự động 9', 'ACTIVE', '517 Phạm Ngũ Lão', 10.747990390202556,
        106.65656839674105, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10009, 10009, 10, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10009, 10009, 10037, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10009, 10009, 10037, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10009, 10009, 'Bảo trì định kỳ 9', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10009, 10009, 10037, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10010, 10010, 'CAB-PROD-1010', 'Tủ tự động 10', 'ACTIVE', '764 Hàm Nghi', 10.721546829119658,
        106.61734874549032, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10010, 10010, 11, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10010, 10010, 10075, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10010, 10010, 10075, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10010, 10010, 'Bảo trì định kỳ 10', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10010, 10010, 10075, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10011, 10011, 'CAB-PROD-1011', 'Tủ tự động 11', 'ACTIVE', '553 Lê Lợi', 10.782323733964963, 106.65661373711548,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10011, 10011, 12, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10011, 10011, 10026, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10011, 10011, 10026, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10011, 10011, 'Bảo trì định kỳ 11', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10011, 10011, 10026, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10012, 10012, 'CAB-PROD-1012', 'Tủ tự động 12', 'ACTIVE', '296 Lý Tự Trọng', 10.769519139828171,
        106.64364039466874, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10012, 10012, 13, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10012, 10012, 10017, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10012, 10012, 10017, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10012, 10012, 'Bảo trì định kỳ 12', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10012, 10012, 10017, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10013, 10013, 'CAB-PROD-1013', 'Tủ tự động 13', 'ACTIVE', '307 Nguyễn Đình Chiểu', 10.720258051827203,
        106.6351239107427, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10013, 10013, 14, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10013, 10013, 10083, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10013, 10013, 10083, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10013, 10013, 'Bảo trì định kỳ 13', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10013, 10013, 10083, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10014, 10014, 'CAB-PROD-1014', 'Tủ tự động 14', 'ACTIVE', '219 Trần Hưng Đạo', 10.778106798461208,
        106.68330907140053, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10014, 10014, 15, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10014, 10014, 10096, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10014, 10014, 10096, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10014, 10014, 'Bảo trì định kỳ 14', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10014, 10014, 10096, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10015, 10015, 'CAB-PROD-1015', 'Tủ tự động 15', 'ACTIVE', '126 Lý Tự Trọng', 10.774679736883636,
        106.62547257933201, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10015, 10015, 16, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10015, 10015, 10041, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10015, 10015, 10041, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10015, 10015, 'Bảo trì định kỳ 15', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10015, 10015, 10041, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10016, 10016, 'CAB-PROD-1016', 'Tủ tự động 16', 'ACTIVE', '890 Nguyễn Đình Chiểu', 10.810721140612236,
        106.70144202937675, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10016, 10016, 17, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10016, 10016, 10007, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10016, 10016, 10007, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10016, 10016, 'Bảo trì định kỳ 16', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10016, 10016, 10007, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10017, 10017, 'CAB-PROD-1017', 'Tủ tự động 17', 'ACTIVE', '758 Lý Tự Trọng', 10.765654496959057,
        106.64819029023145, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10017, 10017, 18, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10017, 10017, 10041, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10017, 10017, 10041, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10017, 10017, 'Bảo trì định kỳ 17', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10017, 10017, 10041, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10018, 10018, 'CAB-PROD-1018', 'Tủ tự động 18', 'ACTIVE', '633 Trần Hưng Đạo', 10.771316276473193,
        106.6776317512981, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10018, 10018, 19, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10018, 10018, 10003, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10018, 10018, 10003, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10018, 10018, 'Bảo trì định kỳ 18', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10018, 10018, 10003, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10019, 10019, 'CAB-PROD-1019', 'Tủ tự động 19', 'ACTIVE', '551 Lý Tự Trọng', 10.805681375018022,
        106.6342831010402, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10019, 10019, 20, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10019, 10019, 10009, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10019, 10019, 10009, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10019, 10019, 'Bảo trì định kỳ 19', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10019, 10019, 10009, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10020, 10020, 'CAB-PROD-1020', 'Tủ tự động 20', 'ACTIVE', '178 Lê Lợi', 10.7520596346205, 106.70694507948183,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10020, 10020, 21, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10020, 10020, 10079, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10020, 10020, 10079, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10020, 10020, 'Bảo trì định kỳ 20', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10020, 10020, 10079, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10021, 10021, 'CAB-PROD-1021', 'Tủ tự động 21', 'ACTIVE', '719 Trần Hưng Đạo', 10.801701480892813,
        106.6986449792552, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10021, 10021, 22, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10021, 10021, 10018, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10021, 10021, 10018, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10021, 10021, 'Bảo trì định kỳ 21', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10021, 10021, 10018, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10022, 10022, 'CAB-PROD-1022', 'Tủ tự động 22', 'ACTIVE', '753 Phạm Ngũ Lão', 10.738280658522209,
        106.6974927023079, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10022, 10022, 23, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10022, 10022, 10064, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10022, 10022, 10064, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10022, 10022, 'Bảo trì định kỳ 22', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10022, 10022, 10064, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10023, 10023, 'CAB-PROD-1023', 'Tủ tự động 23', 'ACTIVE', '851 Trần Hưng Đạo', 10.73689841562356,
        106.6790085402761, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10023, 10023, 24, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10023, 10023, 10005, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10023, 10023, 10005, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10023, 10023, 'Bảo trì định kỳ 23', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10023, 10023, 10005, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10024, 10024, 'CAB-PROD-1024', 'Tủ tự động 24', 'ACTIVE', '482 Hai Bà Trưng', 10.748901185155066,
        106.69275978594044, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10024, 10024, 25, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10024, 10024, 10038, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10024, 10024, 10038, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10024, 10024, 'Bảo trì định kỳ 24', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10024, 10024, 10038, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10025, 10025, 'CAB-PROD-1025', 'Tủ tự động 25', 'ACTIVE', '902 Pasteur', 10.752995155157205, 106.62666062038362,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10025, 10025, 26, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10025, 10025, 10088, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10025, 10025, 10088, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10025, 10025, 'Bảo trì định kỳ 25', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10025, 10025, 10088, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10026, 10026, 'CAB-PROD-1026', 'Tủ tự động 26', 'ACTIVE', '360 Trần Hưng Đạo', 10.756459549780192,
        106.66815344248369, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10026, 10026, 27, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10026, 10026, 10094, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10026, 10026, 10094, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10026, 10026, 'Bảo trì định kỳ 26', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10026, 10026, 10094, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10027, 10027, 'CAB-PROD-1027', 'Tủ tự động 27', 'ACTIVE', '895 Lê Lợi', 10.733994972107526, 106.6831657785551,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10027, 10027, 28, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10027, 10027, 10084, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10027, 10027, 10084, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10027, 10027, 'Bảo trì định kỳ 27', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10027, 10027, 10084, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10028, 10028, 'CAB-PROD-1028', 'Tủ tự động 28', 'ACTIVE', '77 Phạm Ngũ Lão', 10.771062156595802,
        106.66356913757684, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10028, 10028, 29, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10028, 10028, 10037, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10028, 10028, 10037, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10028, 10028, 'Bảo trì định kỳ 28', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10028, 10028, 10037, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10029, 10029, 'CAB-PROD-1029', 'Tủ tự động 29', 'ACTIVE', '484 Hai Bà Trưng', 10.78332402685934,
        106.68537538937962, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10029, 10029, 30, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10029, 10029, 10064, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10029, 10029, 10064, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10029, 10029, 'Bảo trì định kỳ 29', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10029, 10029, 10064, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10030, 10030, 'CAB-PROD-1030', 'Tủ tự động 30', 'ACTIVE', '220 Nguyễn Huệ', 10.720809489571547,
        106.61594531897838, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10030, 10030, 31, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10030, 10030, 10037, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10030, 10030, 10037, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10030, 10030, 'Bảo trì định kỳ 30', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10030, 10030, 10037, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10031, 10031, 'CAB-PROD-1031', 'Tủ tự động 31', 'ACTIVE', '325 Nguyễn Đình Chiểu', 10.717811009557936,
        106.65554421079759, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10031, 10031, 32, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10031, 10031, 10092, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10031, 10031, 10092, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10031, 10031, 'Bảo trì định kỳ 31', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10031, 10031, 10092, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10032, 10032, 'CAB-PROD-1032', 'Tủ tự động 32', 'ACTIVE', '908 Hai Bà Trưng', 10.778431151668642,
        106.70492161406871, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10032, 10032, 33, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10032, 10032, 10017, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10032, 10032, 10017, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10032, 10032, 'Bảo trì định kỳ 32', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10032, 10032, 10017, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10033, 10033, 'CAB-PROD-1033', 'Tủ tự động 33', 'ACTIVE', '630 Lý Tự Trọng', 10.794206385723768,
        106.69066774778982, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10033, 10033, 34, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10033, 10033, 10028, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10033, 10033, 10028, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10033, 10033, 'Bảo trì định kỳ 33', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10033, 10033, 10028, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10034, 10034, 'CAB-PROD-1034', 'Tủ tự động 34', 'ACTIVE', '590 Lý Tự Trọng', 10.798913718061947,
        106.70904210974845, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10034, 10034, 35, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10034, 10034, 10047, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10034, 10034, 10047, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10034, 10034, 'Bảo trì định kỳ 34', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10034, 10034, 10047, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10035, 10035, 'CAB-PROD-1035', 'Tủ tự động 35', 'ACTIVE', '538 Pasteur', 10.774547220205923, 106.67762927468837,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10035, 10035, 36, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10035, 10035, 10007, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10035, 10035, 10007, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10035, 10035, 'Bảo trì định kỳ 35', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10035, 10035, 10007, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10036, 10036, 'CAB-PROD-1036', 'Tủ tự động 36', 'ACTIVE', '202 Nguyễn Đình Chiểu', 10.736419769258942,
        106.61572624996452, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10036, 10036, 37, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10036, 10036, 10080, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10036, 10036, 10080, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10036, 10036, 'Bảo trì định kỳ 36', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10036, 10036, 10080, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10037, 10037, 'CAB-PROD-1037', 'Tủ tự động 37', 'ACTIVE', '64 Nguyễn Huệ', 10.811522125742966,
        106.69846001748985, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10037, 10037, 38, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10037, 10037, 10018, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10037, 10037, 10018, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10037, 10037, 'Bảo trì định kỳ 37', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10037, 10037, 10018, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10038, 10038, 'CAB-PROD-1038', 'Tủ tự động 38', 'ACTIVE', '918 Lê Duẩn', 10.770463507512279, 106.63094589034372,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10038, 10038, 39, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10038, 10038, 10046, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10038, 10038, 10046, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10038, 10038, 'Bảo trì định kỳ 38', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10038, 10038, 10046, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10039, 10039, 'CAB-PROD-1039', 'Tủ tự động 39', 'ACTIVE', '208 Pasteur', 10.74744762909778, 106.61528570131698,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10039, 10039, 40, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10039, 10039, 10030, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10039, 10039, 10030, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10039, 10039, 'Bảo trì định kỳ 39', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10039, 10039, 10030, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10040, 10040, 'CAB-PROD-1040', 'Tủ tự động 40', 'ACTIVE', '407 Nguyễn Huệ', 10.771031866374383,
        106.70511494300436, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10040, 10040, 41, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10040, 10040, 10024, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10040, 10040, 10024, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10040, 10040, 'Bảo trì định kỳ 40', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10040, 10040, 10024, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10041, 10041, 'CAB-PROD-1041', 'Tủ tự động 41', 'ACTIVE', '518 Nguyễn Đình Chiểu', 10.792059389256323,
        106.61590683362614, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10041, 10041, 42, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10041, 10041, 10012, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10041, 10041, 10012, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10041, 10041, 'Bảo trì định kỳ 41', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10041, 10041, 10012, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10042, 10042, 'CAB-PROD-1042', 'Tủ tự động 42', 'ACTIVE', '528 Lê Lợi', 10.752171991912824, 106.69384218908249,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10042, 10042, 43, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10042, 10042, 10086, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10042, 10042, 10086, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10042, 10042, 'Bảo trì định kỳ 42', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10042, 10042, 10086, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10043, 10043, 'CAB-PROD-1043', 'Tủ tự động 43', 'ACTIVE', '224 Hai Bà Trưng', 10.713760156926448,
        106.66798912465893, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10043, 10043, 44, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10043, 10043, 10045, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10043, 10043, 10045, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10043, 10043, 'Bảo trì định kỳ 43', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10043, 10043, 10045, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10044, 10044, 'CAB-PROD-1044', 'Tủ tự động 44', 'ACTIVE', '362 Hai Bà Trưng', 10.781162043063441,
        106.68866612545362, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10044, 10044, 45, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10044, 10044, 10097, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10044, 10044, 10097, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10044, 10044, 'Bảo trì định kỳ 44', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10044, 10044, 10097, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10045, 10045, 'CAB-PROD-1045', 'Tủ tự động 45', 'ACTIVE', '341 Hàm Nghi', 10.73560597749968, 106.64455310765798,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10045, 10045, 46, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10045, 10045, 10044, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10045, 10045, 10044, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10045, 10045, 'Bảo trì định kỳ 45', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10045, 10045, 10044, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10046, 10046, 'CAB-PROD-1046', 'Tủ tự động 46', 'ACTIVE', '56 Pasteur', 10.715172880701292, 106.67097779520365,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10046, 10046, 47, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10046, 10046, 10050, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10046, 10046, 10050, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10046, 10046, 'Bảo trì định kỳ 46', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10046, 10046, 10050, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10047, 10047, 'CAB-PROD-1047', 'Tủ tự động 47', 'ACTIVE', '685 Lê Duẩn', 10.752188262933087, 106.63416309034824,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10047, 10047, 48, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10047, 10047, 10052, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10047, 10047, 10052, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10047, 10047, 'Bảo trì định kỳ 47', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10047, 10047, 10052, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10048, 10048, 'CAB-PROD-1048', 'Tủ tự động 48', 'ACTIVE', '31 Lý Tự Trọng', 10.746732680891482,
        106.65303174417446, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10048, 10048, 49, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10048, 10048, 10004, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10048, 10048, 10004, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10048, 10048, 'Bảo trì định kỳ 48', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10048, 10048, 10004, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10049, 10049, 'CAB-PROD-1049', 'Tủ tự động 49', 'ACTIVE', '180 Phạm Ngũ Lão', 10.783209581913859,
        106.70078087376584, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10049, 10049, 50, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10049, 10049, 10005, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10049, 10049, 10005, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10049, 10049, 'Bảo trì định kỳ 49', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10049, 10049, 10005, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10050, 10050, 'CAB-PROD-1050', 'Tủ tự động 50', 'ACTIVE', '255 Lê Lợi', 10.766477725723439, 106.6576950592302,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10050, 10050, 51, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10050, 10050, 10097, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10050, 10050, 10097, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10050, 10050, 'Bảo trì định kỳ 50', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10050, 10050, 10097, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10051, 10051, 'CAB-PROD-1051', 'Tủ tự động 51', 'ACTIVE', '347 Hàm Nghi', 10.758457894405975,
        106.62247568603017, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10051, 10051, 52, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10051, 10051, 10084, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10051, 10051, 10084, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10051, 10051, 'Bảo trì định kỳ 51', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10051, 10051, 10084, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10052, 10052, 'CAB-PROD-1052', 'Tủ tự động 52', 'ACTIVE', '756 Pasteur', 10.763528300172656, 106.68961261445986,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10052, 10052, 53, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10052, 10052, 10045, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10052, 10052, 10045, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10052, 10052, 'Bảo trì định kỳ 52', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10052, 10052, 10045, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10053, 10053, 'CAB-PROD-1053', 'Tủ tự động 53', 'ACTIVE', '612 Phạm Ngũ Lão', 10.77079533523043,
        106.64945982372201, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10053, 10053, 54, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10053, 10053, 10099, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10053, 10053, 10099, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10053, 10053, 'Bảo trì định kỳ 53', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10053, 10053, 10099, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10054, 10054, 'CAB-PROD-1054', 'Tủ tự động 54', 'ACTIVE', '115 Hàm Nghi', 10.747022410736848,
        106.63637876108841, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10054, 10054, 55, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10054, 10054, 10002, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10054, 10054, 10002, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10054, 10054, 'Bảo trì định kỳ 54', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10054, 10054, 10002, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10055, 10055, 'CAB-PROD-1055', 'Tủ tự động 55', 'ACTIVE', '120 Lê Duẩn', 10.727303129204515, 106.64187105209389,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10055, 10055, 56, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10055, 10055, 10023, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10055, 10055, 10023, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10055, 10055, 'Bảo trì định kỳ 55', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10055, 10055, 10023, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10056, 10056, 'CAB-PROD-1056', 'Tủ tự động 56', 'ACTIVE', '14 Lê Duẩn', 10.755299914350838, 106.63381915820798,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10056, 10056, 57, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10056, 10056, 10023, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10056, 10056, 10023, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10056, 10056, 'Bảo trì định kỳ 56', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10056, 10056, 10023, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10057, 10057, 'CAB-PROD-1057', 'Tủ tự động 57', 'ACTIVE', '499 Lê Lợi', 10.779298345249472, 106.61660661853107,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10057, 10057, 58, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10057, 10057, 10060, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10057, 10057, 10060, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10057, 10057, 'Bảo trì định kỳ 57', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10057, 10057, 10060, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10058, 10058, 'CAB-PROD-1058', 'Tủ tự động 58', 'ACTIVE', '892 Lê Lợi', 10.754824281391466, 106.6546633556775,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10058, 10058, 59, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10058, 10058, 10052, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10058, 10058, 10052, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10058, 10058, 'Bảo trì định kỳ 58', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10058, 10058, 10052, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10059, 10059, 'CAB-PROD-1059', 'Tủ tự động 59', 'ACTIVE', '802 Nguyễn Huệ', 10.78188280264332,
        106.63012233467036, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10059, 10059, 60, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10059, 10059, 10088, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10059, 10059, 10088, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10059, 10059, 'Bảo trì định kỳ 59', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10059, 10059, 10088, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10060, 10060, 'CAB-PROD-1060', 'Tủ tự động 60', 'ACTIVE', '211 Lý Tự Trọng', 10.766684284212932,
        106.68993296284621, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10060, 10060, 61, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10060, 10060, 10094, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10060, 10060, 10094, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10060, 10060, 'Bảo trì định kỳ 60', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10060, 10060, 10094, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10061, 10061, 'CAB-PROD-1061', 'Tủ tự động 61', 'ACTIVE', '376 Hàm Nghi', 10.756214633121452,
        106.69399770098246, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10061, 10061, 62, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10061, 10061, 10005, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10061, 10061, 10005, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10061, 10061, 'Bảo trì định kỳ 61', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10061, 10061, 10005, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10062, 10062, 'CAB-PROD-1062', 'Tủ tự động 62', 'ACTIVE', '502 Nguyễn Huệ', 10.785458484384705,
        106.70913385527236, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10062, 10062, 63, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10062, 10062, 10048, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10062, 10062, 10048, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10062, 10062, 'Bảo trì định kỳ 62', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10062, 10062, 10048, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10063, 10063, 'CAB-PROD-1063', 'Tủ tự động 63', 'ACTIVE', '771 Lê Duẩn', 10.731973677876866, 106.61237168413813,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10063, 10063, 64, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10063, 10063, 10022, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10063, 10063, 10022, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10063, 10063, 'Bảo trì định kỳ 63', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10063, 10063, 10022, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10064, 10064, 'CAB-PROD-1064', 'Tủ tự động 64', 'ACTIVE', '229 Trần Hưng Đạo', 10.755846579743068,
        106.644639429584, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10064, 10064, 65, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10064, 10064, 10073, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10064, 10064, 10073, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10064, 10064, 'Bảo trì định kỳ 64', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10064, 10064, 10073, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10065, 10065, 'CAB-PROD-1065', 'Tủ tự động 65', 'ACTIVE', '182 Pasteur', 10.751722613633007, 106.65304070168355,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10065, 10065, 66, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10065, 10065, 10091, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10065, 10065, 10091, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10065, 10065, 'Bảo trì định kỳ 65', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10065, 10065, 10091, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10066, 10066, 'CAB-PROD-1066', 'Tủ tự động 66', 'ACTIVE', '887 Nguyễn Đình Chiểu', 10.777736257108064,
        106.69348200601203, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10066, 10066, 67, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10066, 10066, 10018, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10066, 10066, 10018, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10066, 10066, 'Bảo trì định kỳ 66', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10066, 10066, 10018, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10067, 10067, 'CAB-PROD-1067', 'Tủ tự động 67', 'ACTIVE', '253 Hai Bà Trưng', 10.755238341166852,
        106.65137166611503, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10067, 10067, 68, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10067, 10067, 10077, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10067, 10067, 10077, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10067, 10067, 'Bảo trì định kỳ 67', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10067, 10067, 10077, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10068, 10068, 'CAB-PROD-1068', 'Tủ tự động 68', 'ACTIVE', '870 Hàm Nghi', 10.737585320916939,
        106.66568352026478, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10068, 10068, 69, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10068, 10068, 10027, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10068, 10068, 10027, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10068, 10068, 'Bảo trì định kỳ 68', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10068, 10068, 10027, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10069, 10069, 'CAB-PROD-1069', 'Tủ tự động 69', 'ACTIVE', '891 Hàm Nghi', 10.799526908207007,
        106.63706596753762, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10069, 10069, 70, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10069, 10069, 10016, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10069, 10069, 10016, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10069, 10069, 'Bảo trì định kỳ 69', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10069, 10069, 10016, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10070, 10070, 'CAB-PROD-1070', 'Tủ tự động 70', 'ACTIVE', '242 Nguyễn Huệ', 10.744157749268552,
        106.6929155899488, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10070, 10070, 71, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10070, 10070, 10005, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10070, 10070, 10005, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10070, 10070, 'Bảo trì định kỳ 70', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10070, 10070, 10005, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10071, 10071, 'CAB-PROD-1071', 'Tủ tự động 71', 'ACTIVE', '318 Pasteur', 10.765252864752092, 106.64582677442037,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10071, 10071, 72, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10071, 10071, 10034, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10071, 10071, 10034, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10071, 10071, 'Bảo trì định kỳ 71', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10071, 10071, 10034, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10072, 10072, 'CAB-PROD-1072', 'Tủ tự động 72', 'ACTIVE', '772 Lý Tự Trọng', 10.757299042634562,
        106.66921657339864, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10072, 10072, 73, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10072, 10072, 10099, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10072, 10072, 10099, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10072, 10072, 'Bảo trì định kỳ 72', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10072, 10072, 10099, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10073, 10073, 'CAB-PROD-1073', 'Tủ tự động 73', 'ACTIVE', '320 Lê Duẩn', 10.763107967384753, 106.68429430453344,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10073, 10073, 74, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10073, 10073, 10030, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10073, 10073, 10030, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10073, 10073, 'Bảo trì định kỳ 73', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10073, 10073, 10030, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10074, 10074, 'CAB-PROD-1074', 'Tủ tự động 74', 'ACTIVE', '508 Hàm Nghi', 10.751118425760106,
        106.70688680210881, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10074, 10074, 75, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10074, 10074, 10007, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10074, 10074, 10007, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10074, 10074, 'Bảo trì định kỳ 74', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10074, 10074, 10007, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10075, 10075, 'CAB-PROD-1075', 'Tủ tự động 75', 'ACTIVE', '747 Lê Lợi', 10.80422897424813, 106.64990047772588,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10075, 10075, 76, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10075, 10075, 10040, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10075, 10075, 10040, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10075, 10075, 'Bảo trì định kỳ 75', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10075, 10075, 10040, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10076, 10076, 'CAB-PROD-1076', 'Tủ tự động 76', 'ACTIVE', '412 Hai Bà Trưng', 10.793254489688099,
        106.63138424713081, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10076, 10076, 77, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10076, 10076, 10001, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10076, 10076, 10001, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10076, 10076, 'Bảo trì định kỳ 76', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10076, 10076, 10001, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10077, 10077, 'CAB-PROD-1077', 'Tủ tự động 77', 'ACTIVE', '634 Lý Tự Trọng', 10.776105534035988,
        106.68983563068004, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10077, 10077, 78, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10077, 10077, 10038, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10077, 10077, 10038, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10077, 10077, 'Bảo trì định kỳ 77', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10077, 10077, 10038, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10078, 10078, 'CAB-PROD-1078', 'Tủ tự động 78', 'ACTIVE', '223 Nguyễn Đình Chiểu', 10.724204368059784,
        106.69994933366576, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10078, 10078, 79, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10078, 10078, 10003, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10078, 10078, 10003, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10078, 10078, 'Bảo trì định kỳ 78', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10078, 10078, 10003, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10079, 10079, 'CAB-PROD-1079', 'Tủ tự động 79', 'ACTIVE', '202 Nguyễn Đình Chiểu', 10.806968657323338,
        106.61912713910213, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10079, 10079, 80, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10079, 10079, 10030, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10079, 10079, 10030, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10079, 10079, 'Bảo trì định kỳ 79', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10079, 10079, 10030, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10080, 10080, 'CAB-PROD-1080', 'Tủ tự động 80', 'ACTIVE', '16 Lê Duẩn', 10.72605666904143, 106.62945448326688,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10080, 10080, 81, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10080, 10080, 10071, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10080, 10080, 10071, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10080, 10080, 'Bảo trì định kỳ 80', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10080, 10080, 10071, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10081, 10081, 'CAB-PROD-1081', 'Tủ tự động 81', 'ACTIVE', '527 Lê Lợi', 10.71617067990488, 106.65811097314021,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10081, 10081, 82, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10081, 10081, 10088, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10081, 10081, 10088, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10081, 10081, 'Bảo trì định kỳ 81', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10081, 10081, 10088, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10082, 10082, 'CAB-PROD-1082', 'Tủ tự động 82', 'ACTIVE', '38 Nguyễn Huệ', 10.807888371913382,
        106.67459691995235, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10082, 10082, 83, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10082, 10082, 10041, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10082, 10082, 10041, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10082, 10082, 'Bảo trì định kỳ 82', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10082, 10082, 10041, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10083, 10083, 'CAB-PROD-1083', 'Tủ tự động 83', 'ACTIVE', '617 Pasteur', 10.73309964685699, 106.66410814777814,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10083, 10083, 84, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10083, 10083, 10090, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10083, 10083, 10090, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10083, 10083, 'Bảo trì định kỳ 83', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10083, 10083, 10090, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10084, 10084, 'CAB-PROD-1084', 'Tủ tự động 84', 'ACTIVE', '148 Phạm Ngũ Lão', 10.721221685954594,
        106.62337889645505, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10084, 10084, 85, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10084, 10084, 10072, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10084, 10084, 10072, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10084, 10084, 'Bảo trì định kỳ 84', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10084, 10084, 10072, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10085, 10085, 'CAB-PROD-1085', 'Tủ tự động 85', 'ACTIVE', '627 Nguyễn Huệ', 10.79416394148917,
        106.67068574425244, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10085, 10085, 86, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10085, 10085, 10016, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10085, 10085, 10016, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10085, 10085, 'Bảo trì định kỳ 85', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10085, 10085, 10016, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10086, 10086, 'CAB-PROD-1086', 'Tủ tự động 86', 'ACTIVE', '45 Nguyễn Huệ', 10.729183632387272,
        106.68297745501573, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10086, 10086, 87, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10086, 10086, 10054, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10086, 10086, 10054, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10086, 10086, 'Bảo trì định kỳ 86', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10086, 10086, 10054, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10087, 10087, 'CAB-PROD-1087', 'Tủ tự động 87', 'ACTIVE', '995 Lê Lợi', 10.788753046480974, 106.64676582563342,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10087, 10087, 88, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10087, 10087, 10007, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10087, 10087, 10007, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10087, 10087, 'Bảo trì định kỳ 87', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10087, 10087, 10007, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10088, 10088, 'CAB-PROD-1088', 'Tủ tự động 88', 'ACTIVE', '707 Lê Duẩn', 10.797539534396378, 106.70707903451846,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10088, 10088, 89, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10088, 10088, 10038, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10088, 10088, 10038, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10088, 10088, 'Bảo trì định kỳ 88', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10088, 10088, 10038, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10089, 10089, 'CAB-PROD-1089', 'Tủ tự động 89', 'ACTIVE', '99 Lê Duẩn', 10.80434278439751, 106.61529435158329,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10089, 10089, 90, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10089, 10089, 10079, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10089, 10089, 10079, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10089, 10089, 'Bảo trì định kỳ 89', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10089, 10089, 10079, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10090, 10090, 'CAB-PROD-1090', 'Tủ tự động 90', 'ACTIVE', '35 Lê Duẩn', 10.802656622165182, 106.66579468852173,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10090, 10090, 91, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10090, 10090, 10072, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10090, 10090, 10072, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10090, 10090, 'Bảo trì định kỳ 90', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10090, 10090, 10072, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10091, 10091, 'CAB-PROD-1091', 'Tủ tự động 91', 'ACTIVE', '123 Lý Tự Trọng', 10.72489983606788,
        106.69429570938924, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10091, 10091, 92, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10091, 10091, 10065, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10091, 10091, 10065, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10091, 10091, 'Bảo trì định kỳ 91', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10091, 10091, 10065, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10092, 10092, 'CAB-PROD-1092', 'Tủ tự động 92', 'ACTIVE', '877 Lê Lợi', 10.779716183747073, 106.67781590788745,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10092, 10092, 93, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10092, 10092, 10015, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10092, 10092, 10015, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10092, 10092, 'Bảo trì định kỳ 92', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10092, 10092, 10015, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10093, 10093, 'CAB-PROD-1093', 'Tủ tự động 93', 'ACTIVE', '804 Lê Duẩn', 10.790371366240654, 106.64847391140945,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10093, 10093, 94, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10093, 10093, 10039, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10093, 10093, 10039, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10093, 10093, 'Bảo trì định kỳ 93', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10093, 10093, 10039, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10094, 10094, 'CAB-PROD-1094', 'Tủ tự động 94', 'ACTIVE', '106 Hàm Nghi', 10.72635203402448, 106.68246755656698,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10094, 10094, 95, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10094, 10094, 10048, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10094, 10094, 10048, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10094, 10094, 'Bảo trì định kỳ 94', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10094, 10094, 10048, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10095, 10095, 'CAB-PROD-1095', 'Tủ tự động 95', 'ACTIVE', '323 Lê Duẩn', 10.798424640554586, 106.70142892333435,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10095, 10095, 96, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10095, 10095, 10003, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10095, 10095, 10003, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10095, 10095, 'Bảo trì định kỳ 95', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10095, 10095, 10003, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10096, 10096, 'CAB-PROD-1096', 'Tủ tự động 96', 'ACTIVE', '352 Nguyễn Huệ', 10.790432087546105,
        106.68418796926312, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10096, 10096, 97, 'SMALL', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10096, 10096, 10090, 'Lỗi sự cố', 'Kẹt cửa', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10096, 10096, 10090, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10096, 10096, 'Bảo trì định kỳ 96', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10096, 10096, 10090, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10097, 10097, 'CAB-PROD-1097', 'Tủ tự động 97', 'ACTIVE', '758 Nguyễn Đình Chiểu', 10.747446029037611,
        106.6413569679073, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10097, 10097, 98, 'LARGE', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10097, 10097, 10038, 'Lỗi sự cố', 'Mất nguồn', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10097, 10097, 10038, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10097, 10097, 'Bảo trì định kỳ 97', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10097, 10097, 10038, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10098, 10098, 'CAB-PROD-1098', 'Tủ tự động 98', 'ACTIVE', '585 Hàm Nghi', 10.717375577543464,
        106.61931265597092, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10098, 10098, 99, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10098, 10098, 10065, 'Lỗi sự cố', 'Mã PIN không chạy', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10098, 10098, 10065, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10098, 10098, 'Bảo trì định kỳ 98', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10098, 10098, 10065, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.lockers (id, store_id, code, name, status, address, latitude, longitude, created_at,
                                   updated_at)
VALUES (10099, 10099, 'CAB-PROD-1099', 'Tủ tự động 99', 'ACTIVE', '509 Hàm Nghi', 10.742978399025336,
        106.69117615215276, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_boxes (id, locker_id, box_number, size, is_active, status, created_at, updated_at)
VALUES (10099, 10099, 100, 'MEDIUM', true, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_reports (id, locker_id, user_id, title, description, status, created_at, updated_at)
VALUES (10099, 10099, 10037, 'Lỗi sự cố', 'Màn hình đơ', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO locker_schema.repair_logs (id, report_id, actor_user_id, note, created_at)
VALUES (10099, 10099, 10037, 'Đã kiểm tra và thay linh kiện', CURRENT_TIMESTAMP);
INSERT INTO locker_schema.maintenance_schedules (id, locker_id, title, interval_days, next_due_at, active, created_at,
                                                 updated_at)
VALUES (10099, 10099, 'Bảo trì định kỳ 99', 30, CURRENT_TIMESTAMP + INTERVAL '10 days', true, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
INSERT INTO locker_schema.locker_report_ratings (id, report_id, user_id, rating, comment, created_at, updated_at)
VALUES (10099, 10099, 10037, 5, 'Rất hài lòng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

\connect
order_db
DELETE
FROM order_schema.orders
WHERE id >= 10000;
DELETE
FROM order_schema.order_details
WHERE id >= 10000;
DELETE
FROM order_schema.order_status_history
WHERE id >= 10000;
DELETE
FROM order_schema.order_ratings
WHERE id >= 10000;
DELETE
FROM order_schema.order_complaints
WHERE id >= 10000;
DELETE
FROM order_schema.promotions
WHERE id >= 10000;
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10000, 'ORD10000', 10000, 'SEND', 'PENDING', 10000, 10000, 34000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10000, 10000, 4, 1, 34000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10000, 10000, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10000, 10000, 10000, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10000, 10000, 10000, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10000, 'PROMO10000', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10001, 'ORD10001', 10001, 'SEND', 'CANCELED', 10001, 10001, 35000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10001, 10001, 6, 1, 35000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10001, 10001, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10001, 10001, 10001, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10001, 10001, 10001, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10001, 'PROMO10001', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10002, 'ORD10002', 10002, 'SEND', 'PENDING', 10002, 10002, 40000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10002, 10002, 9, 1, 40000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10002, 10002, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10002, 10002, 10002, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10002, 10002, 10002, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10002, 'PROMO10002', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10003, 'ORD10003', 10003, 'RENTAL', 'CANCELED', 10003, 10003, 41000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10003, 10003, 1, 1, 41000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10003, 10003, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10003, 10003, 10003, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10003, 10003, 10003, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10003, 'PROMO10003', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10004, 'ORD10004', 10004, 'SEND', 'PENDING', 10004, 10004, 23000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10004, 10004, 5, 1, 23000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10004, 10004, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10004, 10004, 10004, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10004, 10004, 10004, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10004, 'PROMO10004', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10005, 'ORD10005', 10005, 'SEND', 'COMPLETED', 10005, 10005, 44000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10005, 10005, 9, 1, 44000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10005, 10005, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10005, 10005, 10005, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10005, 10005, 10005, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10005, 'PROMO10005', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10006, 'ORD10006', 10006, 'SEND', 'COMPLETED', 10006, 10006, 25000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10006, 10006, 6, 1, 25000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10006, 10006, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10006, 10006, 10006, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10006, 10006, 10006, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10006, 'PROMO10006', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10007, 'ORD10007', 10007, 'SEND', 'PENDING', 10007, 10007, 36000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10007, 10007, 1, 1, 36000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10007, 10007, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10007, 10007, 10007, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10007, 10007, 10007, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10007, 'PROMO10007', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10008, 'ORD10008', 10008, 'RENTAL', 'CANCELED', 10008, 10008, 50000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10008, 10008, 7, 1, 50000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10008, 10008, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10008, 10008, 10008, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10008, 10008, 10008, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10008, 'PROMO10008', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10009, 'ORD10009', 10009, 'SEND', 'COMPLETED', 10009, 10009, 19000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10009, 10009, 2, 1, 19000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10009, 10009, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10009, 10009, 10009, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10009, 10009, 10009, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10009, 'PROMO10009', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10010, 'ORD10010', 10010, 'STORAGE', 'CANCELED', 10010, 10010, 32000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10010, 10010, 9, 1, 32000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10010, 10010, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10010, 10010, 10010, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10010, 10010, 10010, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10010, 'PROMO10010', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10011, 'ORD10011', 10011, 'RENTAL', 'PENDING', 10011, 10011, 24000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10011, 10011, 6, 1, 24000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10011, 10011, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10011, 10011, 10011, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10011, 10011, 10011, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10011, 'PROMO10011', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10012, 'ORD10012', 10012, 'RENTAL', 'COMPLETED', 10012, 10012, 50000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10012, 10012, 9, 1, 50000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10012, 10012, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10012, 10012, 10012, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10012, 10012, 10012, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10012, 'PROMO10012', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10013, 'ORD10013', 10013, 'STORAGE', 'COMPLETED', 10013, 10013, 18000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10013, 10013, 7, 1, 18000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10013, 10013, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10013, 10013, 10013, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10013, 10013, 10013, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10013, 'PROMO10013', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10014, 'ORD10014', 10014, 'STORAGE', 'COMPLETED', 10014, 10014, 44000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10014, 10014, 9, 1, 44000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10014, 10014, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10014, 10014, 10014, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10014, 10014, 10014, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10014, 'PROMO10014', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10015, 'ORD10015', 10015, 'STORAGE', 'COMPLETED', 10015, 10015, 40000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10015, 10015, 7, 1, 40000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10015, 10015, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10015, 10015, 10015, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10015, 10015, 10015, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10015, 'PROMO10015', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10016, 'ORD10016', 10016, 'SEND', 'PENDING', 10016, 10016, 27000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10016, 10016, 6, 1, 27000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10016, 10016, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10016, 10016, 10016, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10016, 10016, 10016, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10016, 'PROMO10016', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10017, 'ORD10017', 10017, 'RENTAL', 'COMPLETED', 10017, 10017, 36000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10017, 10017, 3, 1, 36000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10017, 10017, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10017, 10017, 10017, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10017, 10017, 10017, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10017, 'PROMO10017', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10018, 'ORD10018', 10018, 'SEND', 'COMPLETED', 10018, 10018, 47000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10018, 10018, 7, 1, 47000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10018, 10018, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10018, 10018, 10018, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10018, 10018, 10018, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10018, 'PROMO10018', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10019, 'ORD10019', 10019, 'RENTAL', 'PENDING', 10019, 10019, 34000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10019, 10019, 4, 1, 34000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10019, 10019, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10019, 10019, 10019, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10019, 10019, 10019, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10019, 'PROMO10019', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10020, 'ORD10020', 10020, 'STORAGE', 'CANCELED', 10020, 10020, 14000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10020, 10020, 2, 1, 14000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10020, 10020, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10020, 10020, 10020, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10020, 10020, 10020, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10020, 'PROMO10020', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10021, 'ORD10021', 10021, 'SEND', 'COMPLETED', 10021, 10021, 29000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10021, 10021, 5, 1, 29000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10021, 10021, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10021, 10021, 10021, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10021, 10021, 10021, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10021, 'PROMO10021', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10022, 'ORD10022', 10022, 'STORAGE', 'PENDING', 10022, 10022, 11000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10022, 10022, 7, 1, 11000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10022, 10022, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10022, 10022, 10022, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10022, 10022, 10022, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10022, 'PROMO10022', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10023, 'ORD10023', 10023, 'RENTAL', 'PENDING', 10023, 10023, 18000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10023, 10023, 8, 1, 18000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10023, 10023, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10023, 10023, 10023, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10023, 10023, 10023, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10023, 'PROMO10023', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10024, 'ORD10024', 10024, 'SEND', 'PENDING', 10024, 10024, 28000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10024, 10024, 1, 1, 28000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10024, 10024, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10024, 10024, 10024, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10024, 10024, 10024, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10024, 'PROMO10024', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10025, 'ORD10025', 10025, 'SEND', 'COMPLETED', 10025, 10025, 20000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10025, 10025, 8, 1, 20000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10025, 10025, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10025, 10025, 10025, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10025, 10025, 10025, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10025, 'PROMO10025', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10026, 'ORD10026', 10026, 'SEND', 'COMPLETED', 10026, 10026, 18000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10026, 10026, 6, 1, 18000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10026, 10026, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10026, 10026, 10026, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10026, 10026, 10026, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10026, 'PROMO10026', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10027, 'ORD10027', 10027, 'RENTAL', 'PENDING', 10027, 10027, 19000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10027, 10027, 10, 1, 19000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10027, 10027, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10027, 10027, 10027, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10027, 10027, 10027, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10027, 'PROMO10027', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10028, 'ORD10028', 10028, 'RENTAL', 'COMPLETED', 10028, 10028, 12000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10028, 10028, 1, 1, 12000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10028, 10028, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10028, 10028, 10028, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10028, 10028, 10028, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10028, 'PROMO10028', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10029, 'ORD10029', 10029, 'RENTAL', 'PENDING', 10029, 10029, 36000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10029, 10029, 7, 1, 36000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10029, 10029, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10029, 10029, 10029, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10029, 10029, 10029, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10029, 'PROMO10029', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10030, 'ORD10030', 10030, 'SEND', 'PENDING', 10030, 10030, 44000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10030, 10030, 3, 1, 44000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10030, 10030, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10030, 10030, 10030, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10030, 10030, 10030, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10030, 'PROMO10030', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10031, 'ORD10031', 10031, 'SEND', 'CANCELED', 10031, 10031, 42000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10031, 10031, 2, 1, 42000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10031, 10031, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10031, 10031, 10031, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10031, 10031, 10031, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10031, 'PROMO10031', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10032, 'ORD10032', 10032, 'RENTAL', 'PENDING', 10032, 10032, 22000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10032, 10032, 9, 1, 22000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10032, 10032, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10032, 10032, 10032, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10032, 10032, 10032, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10032, 'PROMO10032', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10033, 'ORD10033', 10033, 'SEND', 'CANCELED', 10033, 10033, 49000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10033, 10033, 1, 1, 49000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10033, 10033, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10033, 10033, 10033, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10033, 10033, 10033, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10033, 'PROMO10033', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10034, 'ORD10034', 10034, 'SEND', 'COMPLETED', 10034, 10034, 18000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10034, 10034, 5, 1, 18000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10034, 10034, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10034, 10034, 10034, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10034, 10034, 10034, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10034, 'PROMO10034', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10035, 'ORD10035', 10035, 'RENTAL', 'CANCELED', 10035, 10035, 38000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10035, 10035, 10, 1, 38000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10035, 10035, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10035, 10035, 10035, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10035, 10035, 10035, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10035, 'PROMO10035', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10036, 'ORD10036', 10036, 'STORAGE', 'COMPLETED', 10036, 10036, 10000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10036, 10036, 5, 1, 10000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10036, 10036, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10036, 10036, 10036, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10036, 10036, 10036, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10036, 'PROMO10036', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10037, 'ORD10037', 10037, 'RENTAL', 'CANCELED', 10037, 10037, 35000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10037, 10037, 2, 1, 35000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10037, 10037, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10037, 10037, 10037, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10037, 10037, 10037, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10037, 'PROMO10037', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10038, 'ORD10038', 10038, 'SEND', 'CANCELED', 10038, 10038, 29000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10038, 10038, 7, 1, 29000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10038, 10038, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10038, 10038, 10038, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10038, 10038, 10038, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10038, 'PROMO10038', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10039, 'ORD10039', 10039, 'RENTAL', 'CANCELED', 10039, 10039, 19000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10039, 10039, 9, 1, 19000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10039, 10039, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10039, 10039, 10039, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10039, 10039, 10039, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10039, 'PROMO10039', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10040, 'ORD10040', 10040, 'STORAGE', 'PENDING', 10040, 10040, 35000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10040, 10040, 7, 1, 35000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10040, 10040, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10040, 10040, 10040, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10040, 10040, 10040, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10040, 'PROMO10040', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10041, 'ORD10041', 10041, 'STORAGE', 'COMPLETED', 10041, 10041, 31000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10041, 10041, 9, 1, 31000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10041, 10041, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10041, 10041, 10041, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10041, 10041, 10041, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10041, 'PROMO10041', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10042, 'ORD10042', 10042, 'STORAGE', 'PENDING', 10042, 10042, 13000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10042, 10042, 10, 1, 13000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10042, 10042, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10042, 10042, 10042, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10042, 10042, 10042, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10042, 'PROMO10042', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10043, 'ORD10043', 10043, 'SEND', 'COMPLETED', 10043, 10043, 27000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10043, 10043, 6, 1, 27000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10043, 10043, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10043, 10043, 10043, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10043, 10043, 10043, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10043, 'PROMO10043', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10044, 'ORD10044', 10044, 'SEND', 'PENDING', 10044, 10044, 46000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10044, 10044, 7, 1, 46000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10044, 10044, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10044, 10044, 10044, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10044, 10044, 10044, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10044, 'PROMO10044', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10045, 'ORD10045', 10045, 'SEND', 'COMPLETED', 10045, 10045, 23000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10045, 10045, 4, 1, 23000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10045, 10045, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10045, 10045, 10045, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10045, 10045, 10045, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10045, 'PROMO10045', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10046, 'ORD10046', 10046, 'SEND', 'PENDING', 10046, 10046, 41000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10046, 10046, 2, 1, 41000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10046, 10046, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10046, 10046, 10046, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10046, 10046, 10046, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10046, 'PROMO10046', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10047, 'ORD10047', 10047, 'STORAGE', 'PENDING', 10047, 10047, 50000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10047, 10047, 3, 1, 50000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10047, 10047, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10047, 10047, 10047, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10047, 10047, 10047, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10047, 'PROMO10047', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10048, 'ORD10048', 10048, 'SEND', 'PENDING', 10048, 10048, 44000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10048, 10048, 3, 1, 44000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10048, 10048, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10048, 10048, 10048, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10048, 10048, 10048, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10048, 'PROMO10048', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10049, 'ORD10049', 10049, 'STORAGE', 'CANCELED', 10049, 10049, 20000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10049, 10049, 4, 1, 20000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10049, 10049, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10049, 10049, 10049, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10049, 10049, 10049, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10049, 'PROMO10049', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10050, 'ORD10050', 10050, 'SEND', 'COMPLETED', 10050, 10050, 29000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10050, 10050, 6, 1, 29000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10050, 10050, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10050, 10050, 10050, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10050, 10050, 10050, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10050, 'PROMO10050', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10051, 'ORD10051', 10051, 'SEND', 'COMPLETED', 10051, 10051, 21000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10051, 10051, 7, 1, 21000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10051, 10051, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10051, 10051, 10051, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10051, 10051, 10051, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10051, 'PROMO10051', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10052, 'ORD10052', 10052, 'SEND', 'COMPLETED', 10052, 10052, 14000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10052, 10052, 10, 1, 14000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10052, 10052, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10052, 10052, 10052, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10052, 10052, 10052, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10052, 'PROMO10052', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10053, 'ORD10053', 10053, 'RENTAL', 'COMPLETED', 10053, 10053, 49000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10053, 10053, 1, 1, 49000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10053, 10053, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10053, 10053, 10053, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10053, 10053, 10053, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10053, 'PROMO10053', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10054, 'ORD10054', 10054, 'STORAGE', 'PENDING', 10054, 10054, 23000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10054, 10054, 7, 1, 23000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10054, 10054, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10054, 10054, 10054, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10054, 10054, 10054, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10054, 'PROMO10054', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10055, 'ORD10055', 10055, 'SEND', 'COMPLETED', 10055, 10055, 19000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10055, 10055, 7, 1, 19000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10055, 10055, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10055, 10055, 10055, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10055, 10055, 10055, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10055, 'PROMO10055', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10056, 'ORD10056', 10056, 'STORAGE', 'CANCELED', 10056, 10056, 23000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10056, 10056, 6, 1, 23000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10056, 10056, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10056, 10056, 10056, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10056, 10056, 10056, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10056, 'PROMO10056', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10057, 'ORD10057', 10057, 'SEND', 'CANCELED', 10057, 10057, 21000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10057, 10057, 7, 1, 21000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10057, 10057, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10057, 10057, 10057, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10057, 10057, 10057, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10057, 'PROMO10057', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10058, 'ORD10058', 10058, 'STORAGE', 'COMPLETED', 10058, 10058, 38000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10058, 10058, 2, 1, 38000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10058, 10058, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10058, 10058, 10058, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10058, 10058, 10058, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10058, 'PROMO10058', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10059, 'ORD10059', 10059, 'SEND', 'PENDING', 10059, 10059, 47000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10059, 10059, 2, 1, 47000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10059, 10059, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10059, 10059, 10059, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10059, 10059, 10059, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10059, 'PROMO10059', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10060, 'ORD10060', 10060, 'SEND', 'CANCELED', 10060, 10060, 23000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10060, 10060, 10, 1, 23000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10060, 10060, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10060, 10060, 10060, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10060, 10060, 10060, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10060, 'PROMO10060', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10061, 'ORD10061', 10061, 'SEND', 'CANCELED', 10061, 10061, 43000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10061, 10061, 1, 1, 43000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10061, 10061, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10061, 10061, 10061, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10061, 10061, 10061, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10061, 'PROMO10061', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10062, 'ORD10062', 10062, 'RENTAL', 'PENDING', 10062, 10062, 22000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10062, 10062, 1, 1, 22000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10062, 10062, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10062, 10062, 10062, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10062, 10062, 10062, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10062, 'PROMO10062', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10063, 'ORD10063', 10063, 'STORAGE', 'COMPLETED', 10063, 10063, 43000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10063, 10063, 10, 1, 43000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10063, 10063, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10063, 10063, 10063, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10063, 10063, 10063, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10063, 'PROMO10063', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10064, 'ORD10064', 10064, 'STORAGE', 'COMPLETED', 10064, 10064, 11000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10064, 10064, 1, 1, 11000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10064, 10064, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10064, 10064, 10064, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10064, 10064, 10064, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10064, 'PROMO10064', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10065, 'ORD10065', 10065, 'SEND', 'CANCELED', 10065, 10065, 28000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10065, 10065, 5, 1, 28000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10065, 10065, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10065, 10065, 10065, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10065, 10065, 10065, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10065, 'PROMO10065', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10066, 'ORD10066', 10066, 'STORAGE', 'CANCELED', 10066, 10066, 25000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10066, 10066, 7, 1, 25000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10066, 10066, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10066, 10066, 10066, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10066, 10066, 10066, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10066, 'PROMO10066', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10067, 'ORD10067', 10067, 'RENTAL', 'CANCELED', 10067, 10067, 45000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10067, 10067, 7, 1, 45000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10067, 10067, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10067, 10067, 10067, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10067, 10067, 10067, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10067, 'PROMO10067', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10068, 'ORD10068', 10068, 'STORAGE', 'COMPLETED', 10068, 10068, 10000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10068, 10068, 3, 1, 10000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10068, 10068, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10068, 10068, 10068, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10068, 10068, 10068, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10068, 'PROMO10068', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10069, 'ORD10069', 10069, 'SEND', 'CANCELED', 10069, 10069, 10000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10069, 10069, 7, 1, 10000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10069, 10069, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10069, 10069, 10069, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10069, 10069, 10069, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10069, 'PROMO10069', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10070, 'ORD10070', 10070, 'STORAGE', 'COMPLETED', 10070, 10070, 36000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10070, 10070, 8, 1, 36000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10070, 10070, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10070, 10070, 10070, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10070, 10070, 10070, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10070, 'PROMO10070', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10071, 'ORD10071', 10071, 'STORAGE', 'COMPLETED', 10071, 10071, 30000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10071, 10071, 1, 1, 30000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10071, 10071, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10071, 10071, 10071, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10071, 10071, 10071, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10071, 'PROMO10071', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10072, 'ORD10072', 10072, 'STORAGE', 'COMPLETED', 10072, 10072, 13000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10072, 10072, 1, 1, 13000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10072, 10072, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10072, 10072, 10072, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10072, 10072, 10072, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10072, 'PROMO10072', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10073, 'ORD10073', 10073, 'STORAGE', 'PENDING', 10073, 10073, 15000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10073, 10073, 8, 1, 15000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10073, 10073, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10073, 10073, 10073, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10073, 10073, 10073, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10073, 'PROMO10073', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10074, 'ORD10074', 10074, 'RENTAL', 'PENDING', 10074, 10074, 27000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10074, 10074, 1, 1, 27000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10074, 10074, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10074, 10074, 10074, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10074, 10074, 10074, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10074, 'PROMO10074', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10075, 'ORD10075', 10075, 'RENTAL', 'PENDING', 10075, 10075, 22000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10075, 10075, 10, 1, 22000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10075, 10075, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10075, 10075, 10075, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10075, 10075, 10075, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10075, 'PROMO10075', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10076, 'ORD10076', 10076, 'RENTAL', 'CANCELED', 10076, 10076, 33000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10076, 10076, 7, 1, 33000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10076, 10076, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10076, 10076, 10076, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10076, 10076, 10076, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10076, 'PROMO10076', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10077, 'ORD10077', 10077, 'STORAGE', 'CANCELED', 10077, 10077, 14000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10077, 10077, 9, 1, 14000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10077, 10077, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10077, 10077, 10077, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10077, 10077, 10077, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10077, 'PROMO10077', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10078, 'ORD10078', 10078, 'SEND', 'PENDING', 10078, 10078, 11000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10078, 10078, 9, 1, 11000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10078, 10078, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10078, 10078, 10078, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10078, 10078, 10078, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10078, 'PROMO10078', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10079, 'ORD10079', 10079, 'STORAGE', 'COMPLETED', 10079, 10079, 46000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10079, 10079, 2, 1, 46000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10079, 10079, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10079, 10079, 10079, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10079, 10079, 10079, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10079, 'PROMO10079', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10080, 'ORD10080', 10080, 'RENTAL', 'COMPLETED', 10080, 10080, 24000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10080, 10080, 4, 1, 24000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10080, 10080, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10080, 10080, 10080, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10080, 10080, 10080, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10080, 'PROMO10080', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10081, 'ORD10081', 10081, 'RENTAL', 'CANCELED', 10081, 10081, 49000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10081, 10081, 4, 1, 49000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10081, 10081, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10081, 10081, 10081, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10081, 10081, 10081, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10081, 'PROMO10081', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10082, 'ORD10082', 10082, 'RENTAL', 'PENDING', 10082, 10082, 29000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10082, 10082, 4, 1, 29000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10082, 10082, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10082, 10082, 10082, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10082, 10082, 10082, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10082, 'PROMO10082', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10083, 'ORD10083', 10083, 'SEND', 'PENDING', 10083, 10083, 49000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10083, 10083, 4, 1, 49000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10083, 10083, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10083, 10083, 10083, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10083, 10083, 10083, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10083, 'PROMO10083', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10084, 'ORD10084', 10084, 'RENTAL', 'CANCELED', 10084, 10084, 29000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10084, 10084, 2, 1, 29000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10084, 10084, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10084, 10084, 10084, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10084, 10084, 10084, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10084, 'PROMO10084', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10085, 'ORD10085', 10085, 'STORAGE', 'CANCELED', 10085, 10085, 43000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10085, 10085, 2, 1, 43000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10085, 10085, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10085, 10085, 10085, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10085, 10085, 10085, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10085, 'PROMO10085', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10086, 'ORD10086', 10086, 'RENTAL', 'PENDING', 10086, 10086, 14000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10086, 10086, 9, 1, 14000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10086, 10086, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10086, 10086, 10086, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10086, 10086, 10086, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10086, 'PROMO10086', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10087, 'ORD10087', 10087, 'SEND', 'COMPLETED', 10087, 10087, 19000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10087, 10087, 10, 1, 19000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10087, 10087, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10087, 10087, 10087, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10087, 10087, 10087, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10087, 'PROMO10087', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10088, 'ORD10088', 10088, 'STORAGE', 'CANCELED', 10088, 10088, 13000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10088, 10088, 8, 1, 13000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10088, 10088, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10088, 10088, 10088, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10088, 10088, 10088, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10088, 'PROMO10088', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10089, 'ORD10089', 10089, 'SEND', 'PENDING', 10089, 10089, 36000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10089, 10089, 1, 1, 36000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10089, 10089, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10089, 10089, 10089, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10089, 10089, 10089, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10089, 'PROMO10089', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10090, 'ORD10090', 10090, 'STORAGE', 'PENDING', 10090, 10090, 40000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10090, 10090, 9, 1, 40000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10090, 10090, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10090, 10090, 10090, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10090, 10090, 10090, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10090, 'PROMO10090', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10091, 'ORD10091', 10091, 'RENTAL', 'PENDING', 10091, 10091, 36000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10091, 10091, 1, 1, 36000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10091, 10091, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10091, 10091, 10091, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10091, 10091, 10091, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10091, 'PROMO10091', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10092, 'ORD10092', 10092, 'RENTAL', 'CANCELED', 10092, 10092, 21000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10092, 10092, 4, 1, 21000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10092, 10092, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10092, 10092, 10092, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10092, 10092, 10092, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10092, 'PROMO10092', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10093, 'ORD10093', 10093, 'STORAGE', 'CANCELED', 10093, 10093, 36000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10093, 10093, 1, 1, 36000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10093, 10093, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10093, 10093, 10093, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10093, 10093, 10093, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10093, 'PROMO10093', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10094, 'ORD10094', 10094, 'SEND', 'CANCELED', 10094, 10094, 48000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10094, 10094, 7, 1, 48000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10094, 10094, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10094, 10094, 10094, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10094, 10094, 10094, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10094, 'PROMO10094', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10095, 'ORD10095', 10095, 'STORAGE', 'PENDING', 10095, 10095, 30000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10095, 10095, 1, 1, 30000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10095, 10095, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10095, 10095, 10095, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10095, 10095, 10095, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10095, 'PROMO10095', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10096, 'ORD10096', 10096, 'SEND', 'PENDING', 10096, 10096, 29000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10096, 10096, 5, 1, 29000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10096, 10096, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10096, 10096, 10096, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10096, 10096, 10096, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10096, 'PROMO10096', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10097, 'ORD10097', 10097, 'RENTAL', 'COMPLETED', 10097, 10097, 13000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10097, 10097, 7, 1, 13000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10097, 10097, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10097, 10097, 10097, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10097, 10097, 10097, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10097, 'PROMO10097', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10098, 'ORD10098', 10098, 'RENTAL', 'CANCELED', 10098, 10098, 14000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10098, 10098, 10, 1, 14000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10098, 10098, 'CANCELED', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10098, 10098, 10098, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10098, 10098, 10098, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10098, 'PROMO10098', 'Sale', 10000, 'ACTIVE');
INSERT INTO order_schema.orders (id, order_code, user_id, type, status, store_id, locker_id, total_price, created_at)
VALUES (10099, 'ORD10099', 10099, 'SEND', 'PENDING', 10099, 10099, 35000, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_details (id, order_id, service_id, quantity, price)
VALUES (10099, 10099, 8, 1, 35000);
INSERT INTO order_schema.order_status_history (id, order_id, new_status, created_at)
VALUES (10099, 10099, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_ratings (id, order_id, user_id, rating, created_at)
VALUES (10099, 10099, 10099, 5, CURRENT_TIMESTAMP);
INSERT INTO order_schema.order_complaints (id, order_id, user_id, description, status, created_at)
VALUES (10099, 10099, 10099, 'Sự cố', 'OPEN', CURRENT_TIMESTAMP);
INSERT INTO order_schema.promotions (id, code, name, discount_value, status)
VALUES (10099, 'PROMO10099', 'Sale', 10000, 'ACTIVE');

\connect
payment_db
DELETE
FROM payment_schema.payments
WHERE id >= 10000;
DELETE
FROM payment_schema.refunds
WHERE id >= 10000;
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10000, 10000, 10000, 34000, 'VNPAY', 'COMPLETED', 'TXN10000', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10000, 10000, 10000, 34000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10001, 10001, 10001, 10000, 'VNPAY', 'COMPLETED', 'TXN10001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10001, 10001, 10001, 10000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10002, 10002, 10002, 26000, 'VNPAY', 'COMPLETED', 'TXN10002', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10002, 10002, 10002, 26000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10003, 10003, 10003, 19000, 'VNPAY', 'COMPLETED', 'TXN10003', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10003, 10003, 10003, 19000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10004, 10004, 10004, 15000, 'VNPAY', 'COMPLETED', 'TXN10004', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10004, 10004, 10004, 15000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10005, 10005, 10005, 43000, 'VNPAY', 'COMPLETED', 'TXN10005', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10005, 10005, 10005, 43000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10006, 10006, 10006, 29000, 'VNPAY', 'COMPLETED', 'TXN10006', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10006, 10006, 10006, 29000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10007, 10007, 10007, 49000, 'VNPAY', 'COMPLETED', 'TXN10007', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10007, 10007, 10007, 49000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10008, 10008, 10008, 10000, 'VNPAY', 'COMPLETED', 'TXN10008', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10008, 10008, 10008, 10000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10009, 10009, 10009, 12000, 'VNPAY', 'COMPLETED', 'TXN10009', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10009, 10009, 10009, 12000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10010, 10010, 10010, 44000, 'VNPAY', 'COMPLETED', 'TXN10010', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10010, 10010, 10010, 44000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10011, 10011, 10011, 30000, 'VNPAY', 'COMPLETED', 'TXN10011', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10011, 10011, 10011, 30000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10012, 10012, 10012, 24000, 'VNPAY', 'COMPLETED', 'TXN10012', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10012, 10012, 10012, 24000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10013, 10013, 10013, 31000, 'VNPAY', 'COMPLETED', 'TXN10013', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10013, 10013, 10013, 31000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10014, 10014, 10014, 12000, 'VNPAY', 'COMPLETED', 'TXN10014', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10014, 10014, 10014, 12000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10015, 10015, 10015, 31000, 'VNPAY', 'COMPLETED', 'TXN10015', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10015, 10015, 10015, 31000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10016, 10016, 10016, 35000, 'VNPAY', 'COMPLETED', 'TXN10016', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10016, 10016, 10016, 35000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10017, 10017, 10017, 19000, 'VNPAY', 'COMPLETED', 'TXN10017', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10017, 10017, 10017, 19000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10018, 10018, 10018, 42000, 'VNPAY', 'COMPLETED', 'TXN10018', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10018, 10018, 10018, 42000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10019, 10019, 10019, 42000, 'VNPAY', 'COMPLETED', 'TXN10019', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10019, 10019, 10019, 42000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10020, 10020, 10020, 22000, 'VNPAY', 'COMPLETED', 'TXN10020', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10020, 10020, 10020, 22000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10021, 10021, 10021, 44000, 'VNPAY', 'COMPLETED', 'TXN10021', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10021, 10021, 10021, 44000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10022, 10022, 10022, 38000, 'VNPAY', 'COMPLETED', 'TXN10022', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10022, 10022, 10022, 38000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10023, 10023, 10023, 14000, 'VNPAY', 'COMPLETED', 'TXN10023', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10023, 10023, 10023, 14000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10024, 10024, 10024, 11000, 'VNPAY', 'COMPLETED', 'TXN10024', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10024, 10024, 10024, 11000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10025, 10025, 10025, 29000, 'VNPAY', 'COMPLETED', 'TXN10025', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10025, 10025, 10025, 29000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10026, 10026, 10026, 28000, 'VNPAY', 'COMPLETED', 'TXN10026', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10026, 10026, 10026, 28000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10027, 10027, 10027, 36000, 'VNPAY', 'COMPLETED', 'TXN10027', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10027, 10027, 10027, 36000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10028, 10028, 10028, 40000, 'VNPAY', 'COMPLETED', 'TXN10028', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10028, 10028, 10028, 40000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10029, 10029, 10029, 13000, 'VNPAY', 'COMPLETED', 'TXN10029', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10029, 10029, 10029, 13000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10030, 10030, 10030, 10000, 'VNPAY', 'COMPLETED', 'TXN10030', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10030, 10030, 10030, 10000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10031, 10031, 10031, 46000, 'VNPAY', 'COMPLETED', 'TXN10031', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10031, 10031, 10031, 46000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10032, 10032, 10032, 15000, 'VNPAY', 'COMPLETED', 'TXN10032', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10032, 10032, 10032, 15000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10033, 10033, 10033, 22000, 'VNPAY', 'COMPLETED', 'TXN10033', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10033, 10033, 10033, 22000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10034, 10034, 10034, 31000, 'VNPAY', 'COMPLETED', 'TXN10034', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10034, 10034, 10034, 31000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10035, 10035, 10035, 21000, 'VNPAY', 'COMPLETED', 'TXN10035', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10035, 10035, 10035, 21000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10036, 10036, 10036, 12000, 'VNPAY', 'COMPLETED', 'TXN10036', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10036, 10036, 10036, 12000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10037, 10037, 10037, 21000, 'VNPAY', 'COMPLETED', 'TXN10037', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10037, 10037, 10037, 21000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10038, 10038, 10038, 50000, 'VNPAY', 'COMPLETED', 'TXN10038', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10038, 10038, 10038, 50000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10039, 10039, 10039, 48000, 'VNPAY', 'COMPLETED', 'TXN10039', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10039, 10039, 10039, 48000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10040, 10040, 10040, 14000, 'VNPAY', 'COMPLETED', 'TXN10040', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10040, 10040, 10040, 14000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10041, 10041, 10041, 30000, 'VNPAY', 'COMPLETED', 'TXN10041', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10041, 10041, 10041, 30000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10042, 10042, 10042, 35000, 'VNPAY', 'COMPLETED', 'TXN10042', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10042, 10042, 10042, 35000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10043, 10043, 10043, 43000, 'VNPAY', 'COMPLETED', 'TXN10043', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10043, 10043, 10043, 43000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10044, 10044, 10044, 36000, 'VNPAY', 'COMPLETED', 'TXN10044', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10044, 10044, 10044, 36000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10045, 10045, 10045, 27000, 'VNPAY', 'COMPLETED', 'TXN10045', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10045, 10045, 10045, 27000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10046, 10046, 10046, 13000, 'VNPAY', 'COMPLETED', 'TXN10046', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10046, 10046, 10046, 13000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10047, 10047, 10047, 18000, 'VNPAY', 'COMPLETED', 'TXN10047', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10047, 10047, 10047, 18000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10048, 10048, 10048, 18000, 'VNPAY', 'COMPLETED', 'TXN10048', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10048, 10048, 10048, 18000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10049, 10049, 10049, 31000, 'VNPAY', 'COMPLETED', 'TXN10049', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10049, 10049, 10049, 31000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10050, 10050, 10050, 24000, 'VNPAY', 'COMPLETED', 'TXN10050', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10050, 10050, 10050, 24000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10051, 10051, 10051, 37000, 'VNPAY', 'COMPLETED', 'TXN10051', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10051, 10051, 10051, 37000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10052, 10052, 10052, 44000, 'VNPAY', 'COMPLETED', 'TXN10052', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10052, 10052, 10052, 44000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10053, 10053, 10053, 27000, 'VNPAY', 'COMPLETED', 'TXN10053', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10053, 10053, 10053, 27000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10054, 10054, 10054, 16000, 'VNPAY', 'COMPLETED', 'TXN10054', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10054, 10054, 10054, 16000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10055, 10055, 10055, 32000, 'VNPAY', 'COMPLETED', 'TXN10055', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10055, 10055, 10055, 32000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10056, 10056, 10056, 13000, 'VNPAY', 'COMPLETED', 'TXN10056', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10056, 10056, 10056, 13000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10057, 10057, 10057, 21000, 'VNPAY', 'COMPLETED', 'TXN10057', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10057, 10057, 10057, 21000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10058, 10058, 10058, 44000, 'VNPAY', 'COMPLETED', 'TXN10058', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10058, 10058, 10058, 44000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10059, 10059, 10059, 14000, 'VNPAY', 'COMPLETED', 'TXN10059', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10059, 10059, 10059, 14000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10060, 10060, 10060, 12000, 'VNPAY', 'COMPLETED', 'TXN10060', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10060, 10060, 10060, 12000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10061, 10061, 10061, 33000, 'VNPAY', 'COMPLETED', 'TXN10061', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10061, 10061, 10061, 33000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10062, 10062, 10062, 50000, 'VNPAY', 'COMPLETED', 'TXN10062', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10062, 10062, 10062, 50000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10063, 10063, 10063, 45000, 'VNPAY', 'COMPLETED', 'TXN10063', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10063, 10063, 10063, 45000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10064, 10064, 10064, 42000, 'VNPAY', 'COMPLETED', 'TXN10064', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10064, 10064, 10064, 42000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10065, 10065, 10065, 40000, 'VNPAY', 'COMPLETED', 'TXN10065', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10065, 10065, 10065, 40000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10066, 10066, 10066, 20000, 'VNPAY', 'COMPLETED', 'TXN10066', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10066, 10066, 10066, 20000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10067, 10067, 10067, 41000, 'VNPAY', 'COMPLETED', 'TXN10067', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10067, 10067, 10067, 41000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10068, 10068, 10068, 34000, 'VNPAY', 'COMPLETED', 'TXN10068', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10068, 10068, 10068, 34000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10069, 10069, 10069, 46000, 'VNPAY', 'COMPLETED', 'TXN10069', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10069, 10069, 10069, 46000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10070, 10070, 10070, 14000, 'VNPAY', 'COMPLETED', 'TXN10070', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10070, 10070, 10070, 14000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10071, 10071, 10071, 21000, 'VNPAY', 'COMPLETED', 'TXN10071', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10071, 10071, 10071, 21000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10072, 10072, 10072, 49000, 'VNPAY', 'COMPLETED', 'TXN10072', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10072, 10072, 10072, 49000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10073, 10073, 10073, 32000, 'VNPAY', 'COMPLETED', 'TXN10073', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10073, 10073, 10073, 32000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10074, 10074, 10074, 43000, 'VNPAY', 'COMPLETED', 'TXN10074', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10074, 10074, 10074, 43000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10075, 10075, 10075, 23000, 'VNPAY', 'COMPLETED', 'TXN10075', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10075, 10075, 10075, 23000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10076, 10076, 10076, 38000, 'VNPAY', 'COMPLETED', 'TXN10076', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10076, 10076, 10076, 38000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10077, 10077, 10077, 43000, 'VNPAY', 'COMPLETED', 'TXN10077', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10077, 10077, 10077, 43000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10078, 10078, 10078, 45000, 'VNPAY', 'COMPLETED', 'TXN10078', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10078, 10078, 10078, 45000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10079, 10079, 10079, 42000, 'VNPAY', 'COMPLETED', 'TXN10079', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10079, 10079, 10079, 42000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10080, 10080, 10080, 16000, 'VNPAY', 'COMPLETED', 'TXN10080', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10080, 10080, 10080, 16000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10081, 10081, 10081, 29000, 'VNPAY', 'COMPLETED', 'TXN10081', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10081, 10081, 10081, 29000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10082, 10082, 10082, 33000, 'VNPAY', 'COMPLETED', 'TXN10082', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10082, 10082, 10082, 33000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10083, 10083, 10083, 45000, 'VNPAY', 'COMPLETED', 'TXN10083', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10083, 10083, 10083, 45000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10084, 10084, 10084, 20000, 'VNPAY', 'COMPLETED', 'TXN10084', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10084, 10084, 10084, 20000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10085, 10085, 10085, 11000, 'VNPAY', 'COMPLETED', 'TXN10085', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10085, 10085, 10085, 11000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10086, 10086, 10086, 13000, 'VNPAY', 'COMPLETED', 'TXN10086', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10086, 10086, 10086, 13000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10087, 10087, 10087, 31000, 'VNPAY', 'COMPLETED', 'TXN10087', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10087, 10087, 10087, 31000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10088, 10088, 10088, 24000, 'VNPAY', 'COMPLETED', 'TXN10088', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10088, 10088, 10088, 24000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10089, 10089, 10089, 43000, 'VNPAY', 'COMPLETED', 'TXN10089', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10089, 10089, 10089, 43000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10090, 10090, 10090, 12000, 'VNPAY', 'COMPLETED', 'TXN10090', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10090, 10090, 10090, 12000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10091, 10091, 10091, 48000, 'VNPAY', 'COMPLETED', 'TXN10091', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10091, 10091, 10091, 48000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10092, 10092, 10092, 21000, 'VNPAY', 'COMPLETED', 'TXN10092', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10092, 10092, 10092, 21000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10093, 10093, 10093, 30000, 'VNPAY', 'COMPLETED', 'TXN10093', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10093, 10093, 10093, 30000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10094, 10094, 10094, 25000, 'VNPAY', 'COMPLETED', 'TXN10094', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10094, 10094, 10094, 25000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10095, 10095, 10095, 13000, 'VNPAY', 'COMPLETED', 'TXN10095', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10095, 10095, 10095, 13000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10096, 10096, 10096, 22000, 'VNPAY', 'COMPLETED', 'TXN10096', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10096, 10096, 10096, 22000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10097, 10097, 10097, 32000, 'VNPAY', 'COMPLETED', 'TXN10097', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10097, 10097, 10097, 32000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10098, 10098, 10098, 48000, 'VNPAY', 'COMPLETED', 'TXN10098', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10098, 10098, 10098, 48000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO payment_schema.payments (id, order_id, user_id, amount, method, status, reference_transaction_id,
                                     created_at, updated_at)
VALUES (10099, 10099, 10099, 11000, 'VNPAY', 'COMPLETED', 'TXN10099', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO payment_schema.refunds (id, payment_id, order_id, amount, reason, status, requested_at)
VALUES (10099, 10099, 10099, 11000, 'Khách yêu cầu huỷ', 'COMPLETED', CURRENT_TIMESTAMP);

\connect
iot_db
DELETE
FROM iot_schema.device_statuses
WHERE id >= 10000;
DELETE
FROM iot_schema.box_access_logs
WHERE id >= 10000;
DELETE
FROM iot_schema.access_attempts
WHERE box_id >= 10000;
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10000, 'dev_10000', 10000, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10000, 10000, 10000, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10000, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10001, 'dev_10001', 10001, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10001, 10001, 10001, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10001, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10002, 'dev_10002', 10002, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10002, 10002, 10002, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10002, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10003, 'dev_10003', 10003, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10003, 10003, 10003, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10003, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10004, 'dev_10004', 10004, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10004, 10004, 10004, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10004, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10005, 'dev_10005', 10005, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10005, 10005, 10005, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10005, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10006, 'dev_10006', 10006, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10006, 10006, 10006, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10006, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10007, 'dev_10007', 10007, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10007, 10007, 10007, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10007, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10008, 'dev_10008', 10008, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10008, 10008, 10008, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10008, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10009, 'dev_10009', 10009, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10009, 10009, 10009, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10009, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10010, 'dev_10010', 10010, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10010, 10010, 10010, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10010, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10011, 'dev_10011', 10011, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10011, 10011, 10011, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10011, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10012, 'dev_10012', 10012, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10012, 10012, 10012, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10012, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10013, 'dev_10013', 10013, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10013, 10013, 10013, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10013, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10014, 'dev_10014', 10014, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10014, 10014, 10014, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10014, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10015, 'dev_10015', 10015, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10015, 10015, 10015, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10015, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10016, 'dev_10016', 10016, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10016, 10016, 10016, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10016, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10017, 'dev_10017', 10017, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10017, 10017, 10017, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10017, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10018, 'dev_10018', 10018, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10018, 10018, 10018, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10018, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10019, 'dev_10019', 10019, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10019, 10019, 10019, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10019, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10020, 'dev_10020', 10020, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10020, 10020, 10020, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10020, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10021, 'dev_10021', 10021, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10021, 10021, 10021, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10021, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10022, 'dev_10022', 10022, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10022, 10022, 10022, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10022, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10023, 'dev_10023', 10023, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10023, 10023, 10023, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10023, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10024, 'dev_10024', 10024, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10024, 10024, 10024, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10024, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10025, 'dev_10025', 10025, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10025, 10025, 10025, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10025, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10026, 'dev_10026', 10026, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10026, 10026, 10026, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10026, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10027, 'dev_10027', 10027, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10027, 10027, 10027, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10027, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10028, 'dev_10028', 10028, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10028, 10028, 10028, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10028, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10029, 'dev_10029', 10029, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10029, 10029, 10029, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10029, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10030, 'dev_10030', 10030, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10030, 10030, 10030, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10030, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10031, 'dev_10031', 10031, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10031, 10031, 10031, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10031, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10032, 'dev_10032', 10032, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10032, 10032, 10032, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10032, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10033, 'dev_10033', 10033, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10033, 10033, 10033, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10033, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10034, 'dev_10034', 10034, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10034, 10034, 10034, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10034, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10035, 'dev_10035', 10035, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10035, 10035, 10035, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10035, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10036, 'dev_10036', 10036, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10036, 10036, 10036, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10036, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10037, 'dev_10037', 10037, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10037, 10037, 10037, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10037, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10038, 'dev_10038', 10038, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10038, 10038, 10038, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10038, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10039, 'dev_10039', 10039, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10039, 10039, 10039, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10039, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10040, 'dev_10040', 10040, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10040, 10040, 10040, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10040, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10041, 'dev_10041', 10041, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10041, 10041, 10041, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10041, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10042, 'dev_10042', 10042, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10042, 10042, 10042, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10042, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10043, 'dev_10043', 10043, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10043, 10043, 10043, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10043, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10044, 'dev_10044', 10044, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10044, 10044, 10044, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10044, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10045, 'dev_10045', 10045, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10045, 10045, 10045, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10045, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10046, 'dev_10046', 10046, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10046, 10046, 10046, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10046, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10047, 'dev_10047', 10047, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10047, 10047, 10047, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10047, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10048, 'dev_10048', 10048, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10048, 10048, 10048, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10048, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10049, 'dev_10049', 10049, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10049, 10049, 10049, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10049, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10050, 'dev_10050', 10050, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10050, 10050, 10050, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10050, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10051, 'dev_10051', 10051, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10051, 10051, 10051, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10051, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10052, 'dev_10052', 10052, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10052, 10052, 10052, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10052, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10053, 'dev_10053', 10053, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10053, 10053, 10053, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10053, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10054, 'dev_10054', 10054, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10054, 10054, 10054, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10054, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10055, 'dev_10055', 10055, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10055, 10055, 10055, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10055, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10056, 'dev_10056', 10056, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10056, 10056, 10056, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10056, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10057, 'dev_10057', 10057, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10057, 10057, 10057, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10057, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10058, 'dev_10058', 10058, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10058, 10058, 10058, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10058, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10059, 'dev_10059', 10059, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10059, 10059, 10059, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10059, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10060, 'dev_10060', 10060, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10060, 10060, 10060, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10060, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10061, 'dev_10061', 10061, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10061, 10061, 10061, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10061, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10062, 'dev_10062', 10062, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10062, 10062, 10062, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10062, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10063, 'dev_10063', 10063, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10063, 10063, 10063, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10063, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10064, 'dev_10064', 10064, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10064, 10064, 10064, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10064, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10065, 'dev_10065', 10065, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10065, 10065, 10065, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10065, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10066, 'dev_10066', 10066, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10066, 10066, 10066, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10066, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10067, 'dev_10067', 10067, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10067, 10067, 10067, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10067, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10068, 'dev_10068', 10068, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10068, 10068, 10068, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10068, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10069, 'dev_10069', 10069, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10069, 10069, 10069, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10069, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10070, 'dev_10070', 10070, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10070, 10070, 10070, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10070, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10071, 'dev_10071', 10071, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10071, 10071, 10071, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10071, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10072, 'dev_10072', 10072, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10072, 10072, 10072, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10072, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10073, 'dev_10073', 10073, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10073, 10073, 10073, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10073, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10074, 'dev_10074', 10074, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10074, 10074, 10074, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10074, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10075, 'dev_10075', 10075, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10075, 10075, 10075, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10075, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10076, 'dev_10076', 10076, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10076, 10076, 10076, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10076, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10077, 'dev_10077', 10077, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10077, 10077, 10077, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10077, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10078, 'dev_10078', 10078, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10078, 10078, 10078, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10078, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10079, 'dev_10079', 10079, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10079, 10079, 10079, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10079, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10080, 'dev_10080', 10080, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10080, 10080, 10080, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10080, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10081, 'dev_10081', 10081, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10081, 10081, 10081, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10081, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10082, 'dev_10082', 10082, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10082, 10082, 10082, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10082, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10083, 'dev_10083', 10083, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10083, 10083, 10083, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10083, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10084, 'dev_10084', 10084, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10084, 10084, 10084, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10084, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10085, 'dev_10085', 10085, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10085, 10085, 10085, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10085, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10086, 'dev_10086', 10086, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10086, 10086, 10086, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10086, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10087, 'dev_10087', 10087, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10087, 10087, 10087, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10087, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10088, 'dev_10088', 10088, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10088, 10088, 10088, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10088, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10089, 'dev_10089', 10089, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10089, 10089, 10089, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10089, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10090, 'dev_10090', 10090, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10090, 10090, 10090, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10090, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10091, 'dev_10091', 10091, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10091, 10091, 10091, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10091, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10092, 'dev_10092', 10092, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10092, 10092, 10092, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10092, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10093, 'dev_10093', 10093, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10093, 10093, 10093, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10093, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10094, 'dev_10094', 10094, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10094, 10094, 10094, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10094, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10095, 'dev_10095', 10095, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10095, 10095, 10095, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10095, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10096, 'dev_10096', 10096, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10096, 10096, 10096, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10096, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10097, 'dev_10097', 10097, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10097, 10097, 10097, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10097, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10098, 'dev_10098', 10098, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10098, 10098, 10098, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10098, 1);
INSERT INTO iot_schema.device_statuses (id, device_id, locker_id, status, last_seen_at)
VALUES (10099, 'dev_10099', 10099, 'ONLINE', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.box_access_logs (id, box_id, actor_user_id, credential_type, result, created_at)
VALUES (10099, 10099, 10099, 'PIN', 'SUCCESS', CURRENT_TIMESTAMP);
INSERT INTO iot_schema.access_attempts (box_id, failed_count)
VALUES (10099, 1);

\connect
notification_db
DELETE
FROM notification_schema.notifications
WHERE id >= 10000;
DELETE
FROM notification_schema.fcm_tokens
WHERE id >= 10000;
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10000, 10000, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10000, 10000, 'fcm_10000', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10001, 10001, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10001, 10001, 'fcm_10001', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10002, 10002, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10002, 10002, 'fcm_10002', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10003, 10003, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10003, 10003, 'fcm_10003', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10004, 10004, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10004, 10004, 'fcm_10004', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10005, 10005, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10005, 10005, 'fcm_10005', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10006, 10006, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10006, 10006, 'fcm_10006', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10007, 10007, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10007, 10007, 'fcm_10007', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10008, 10008, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10008, 10008, 'fcm_10008', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10009, 10009, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10009, 10009, 'fcm_10009', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10010, 10010, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10010, 10010, 'fcm_10010', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10011, 10011, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10011, 10011, 'fcm_10011', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10012, 10012, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10012, 10012, 'fcm_10012', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10013, 10013, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10013, 10013, 'fcm_10013', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10014, 10014, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10014, 10014, 'fcm_10014', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10015, 10015, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10015, 10015, 'fcm_10015', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10016, 10016, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10016, 10016, 'fcm_10016', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10017, 10017, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10017, 10017, 'fcm_10017', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10018, 10018, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10018, 10018, 'fcm_10018', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10019, 10019, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10019, 10019, 'fcm_10019', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10020, 10020, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10020, 10020, 'fcm_10020', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10021, 10021, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10021, 10021, 'fcm_10021', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10022, 10022, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10022, 10022, 'fcm_10022', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10023, 10023, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10023, 10023, 'fcm_10023', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10024, 10024, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10024, 10024, 'fcm_10024', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10025, 10025, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10025, 10025, 'fcm_10025', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10026, 10026, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10026, 10026, 'fcm_10026', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10027, 10027, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10027, 10027, 'fcm_10027', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10028, 10028, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10028, 10028, 'fcm_10028', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10029, 10029, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10029, 10029, 'fcm_10029', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10030, 10030, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10030, 10030, 'fcm_10030', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10031, 10031, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10031, 10031, 'fcm_10031', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10032, 10032, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10032, 10032, 'fcm_10032', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10033, 10033, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10033, 10033, 'fcm_10033', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10034, 10034, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10034, 10034, 'fcm_10034', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10035, 10035, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10035, 10035, 'fcm_10035', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10036, 10036, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10036, 10036, 'fcm_10036', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10037, 10037, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10037, 10037, 'fcm_10037', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10038, 10038, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10038, 10038, 'fcm_10038', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10039, 10039, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10039, 10039, 'fcm_10039', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10040, 10040, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10040, 10040, 'fcm_10040', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10041, 10041, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10041, 10041, 'fcm_10041', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10042, 10042, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10042, 10042, 'fcm_10042', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10043, 10043, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10043, 10043, 'fcm_10043', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10044, 10044, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10044, 10044, 'fcm_10044', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10045, 10045, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10045, 10045, 'fcm_10045', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10046, 10046, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10046, 10046, 'fcm_10046', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10047, 10047, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10047, 10047, 'fcm_10047', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10048, 10048, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10048, 10048, 'fcm_10048', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10049, 10049, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10049, 10049, 'fcm_10049', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10050, 10050, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10050, 10050, 'fcm_10050', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10051, 10051, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10051, 10051, 'fcm_10051', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10052, 10052, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10052, 10052, 'fcm_10052', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10053, 10053, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10053, 10053, 'fcm_10053', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10054, 10054, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10054, 10054, 'fcm_10054', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10055, 10055, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10055, 10055, 'fcm_10055', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10056, 10056, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10056, 10056, 'fcm_10056', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10057, 10057, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10057, 10057, 'fcm_10057', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10058, 10058, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10058, 10058, 'fcm_10058', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10059, 10059, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10059, 10059, 'fcm_10059', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10060, 10060, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10060, 10060, 'fcm_10060', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10061, 10061, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10061, 10061, 'fcm_10061', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10062, 10062, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10062, 10062, 'fcm_10062', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10063, 10063, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10063, 10063, 'fcm_10063', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10064, 10064, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10064, 10064, 'fcm_10064', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10065, 10065, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10065, 10065, 'fcm_10065', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10066, 10066, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10066, 10066, 'fcm_10066', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10067, 10067, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10067, 10067, 'fcm_10067', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10068, 10068, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10068, 10068, 'fcm_10068', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10069, 10069, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10069, 10069, 'fcm_10069', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10070, 10070, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10070, 10070, 'fcm_10070', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10071, 10071, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10071, 10071, 'fcm_10071', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10072, 10072, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10072, 10072, 'fcm_10072', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10073, 10073, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10073, 10073, 'fcm_10073', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10074, 10074, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10074, 10074, 'fcm_10074', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10075, 10075, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10075, 10075, 'fcm_10075', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10076, 10076, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10076, 10076, 'fcm_10076', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10077, 10077, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10077, 10077, 'fcm_10077', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10078, 10078, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10078, 10078, 'fcm_10078', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10079, 10079, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10079, 10079, 'fcm_10079', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10080, 10080, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10080, 10080, 'fcm_10080', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10081, 10081, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10081, 10081, 'fcm_10081', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10082, 10082, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10082, 10082, 'fcm_10082', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10083, 10083, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10083, 10083, 'fcm_10083', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10084, 10084, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10084, 10084, 'fcm_10084', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10085, 10085, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10085, 10085, 'fcm_10085', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10086, 10086, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10086, 10086, 'fcm_10086', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10087, 10087, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10087, 10087, 'fcm_10087', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10088, 10088, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10088, 10088, 'fcm_10088', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10089, 10089, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10089, 10089, 'fcm_10089', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10090, 10090, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10090, 10090, 'fcm_10090', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10091, 10091, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10091, 10091, 'fcm_10091', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10092, 10092, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10092, 10092, 'fcm_10092', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10093, 10093, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10093, 10093, 'fcm_10093', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10094, 10094, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10094, 10094, 'fcm_10094', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10095, 10095, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10095, 10095, 'fcm_10095', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10096, 10096, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10096, 10096, 'fcm_10096', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10097, 10097, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10097, 10097, 'fcm_10097', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10098, 10098, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10098, 10098, 'fcm_10098', 'ANDROID', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.notifications (id, user_id, title, message, is_read, type, created_at)
VALUES (10099, 10099, 'Title', 'Message body', false, 'ORDER_UPDATE', CURRENT_TIMESTAMP);
INSERT INTO notification_schema.fcm_tokens (id, user_id, token, device_type, created_at)
VALUES (10099, 10099, 'fcm_10099', 'ANDROID', CURRENT_TIMESTAMP);

\connect
loyalty_db
DELETE
FROM loyalty_schema.loyalty_accounts
WHERE id >= 10000;
DELETE
FROM loyalty_schema.point_transactions
WHERE id >= 10000;
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10000, 10000, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10000, 10000, 10000, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10001, 10001, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10001, 10001, 10001, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10002, 10002, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10002, 10002, 10002, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10003, 10003, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10003, 10003, 10003, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10004, 10004, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10004, 10004, 10004, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10005, 10005, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10005, 10005, 10005, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10006, 10006, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10006, 10006, 10006, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10007, 10007, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10007, 10007, 10007, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10008, 10008, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10008, 10008, 10008, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10009, 10009, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10009, 10009, 10009, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10010, 10010, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10010, 10010, 10010, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10011, 10011, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10011, 10011, 10011, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10012, 10012, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10012, 10012, 10012, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10013, 10013, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10013, 10013, 10013, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10014, 10014, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10014, 10014, 10014, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10015, 10015, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10015, 10015, 10015, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10016, 10016, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10016, 10016, 10016, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10017, 10017, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10017, 10017, 10017, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10018, 10018, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10018, 10018, 10018, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10019, 10019, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10019, 10019, 10019, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10020, 10020, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10020, 10020, 10020, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10021, 10021, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10021, 10021, 10021, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10022, 10022, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10022, 10022, 10022, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10023, 10023, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10023, 10023, 10023, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10024, 10024, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10024, 10024, 10024, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10025, 10025, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10025, 10025, 10025, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10026, 10026, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10026, 10026, 10026, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10027, 10027, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10027, 10027, 10027, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10028, 10028, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10028, 10028, 10028, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10029, 10029, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10029, 10029, 10029, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10030, 10030, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10030, 10030, 10030, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10031, 10031, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10031, 10031, 10031, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10032, 10032, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10032, 10032, 10032, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10033, 10033, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10033, 10033, 10033, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10034, 10034, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10034, 10034, 10034, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10035, 10035, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10035, 10035, 10035, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10036, 10036, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10036, 10036, 10036, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10037, 10037, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10037, 10037, 10037, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10038, 10038, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10038, 10038, 10038, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10039, 10039, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10039, 10039, 10039, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10040, 10040, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10040, 10040, 10040, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10041, 10041, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10041, 10041, 10041, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10042, 10042, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10042, 10042, 10042, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10043, 10043, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10043, 10043, 10043, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10044, 10044, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10044, 10044, 10044, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10045, 10045, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10045, 10045, 10045, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10046, 10046, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10046, 10046, 10046, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10047, 10047, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10047, 10047, 10047, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10048, 10048, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10048, 10048, 10048, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10049, 10049, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10049, 10049, 10049, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10050, 10050, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10050, 10050, 10050, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10051, 10051, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10051, 10051, 10051, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10052, 10052, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10052, 10052, 10052, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10053, 10053, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10053, 10053, 10053, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10054, 10054, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10054, 10054, 10054, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10055, 10055, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10055, 10055, 10055, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10056, 10056, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10056, 10056, 10056, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10057, 10057, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10057, 10057, 10057, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10058, 10058, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10058, 10058, 10058, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10059, 10059, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10059, 10059, 10059, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10060, 10060, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10060, 10060, 10060, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10061, 10061, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10061, 10061, 10061, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10062, 10062, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10062, 10062, 10062, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10063, 10063, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10063, 10063, 10063, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10064, 10064, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10064, 10064, 10064, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10065, 10065, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10065, 10065, 10065, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10066, 10066, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10066, 10066, 10066, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10067, 10067, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10067, 10067, 10067, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10068, 10068, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10068, 10068, 10068, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10069, 10069, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10069, 10069, 10069, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10070, 10070, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10070, 10070, 10070, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10071, 10071, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10071, 10071, 10071, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10072, 10072, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10072, 10072, 10072, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10073, 10073, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10073, 10073, 10073, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10074, 10074, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10074, 10074, 10074, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10075, 10075, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10075, 10075, 10075, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10076, 10076, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10076, 10076, 10076, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10077, 10077, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10077, 10077, 10077, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10078, 10078, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10078, 10078, 10078, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10079, 10079, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10079, 10079, 10079, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10080, 10080, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10080, 10080, 10080, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10081, 10081, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10081, 10081, 10081, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10082, 10082, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10082, 10082, 10082, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10083, 10083, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10083, 10083, 10083, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10084, 10084, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10084, 10084, 10084, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10085, 10085, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10085, 10085, 10085, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10086, 10086, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10086, 10086, 10086, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10087, 10087, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10087, 10087, 10087, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10088, 10088, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10088, 10088, 10088, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10089, 10089, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10089, 10089, 10089, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10090, 10090, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10090, 10090, 10090, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10091, 10091, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10091, 10091, 10091, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10092, 10092, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10092, 10092, 10092, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10093, 10093, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10093, 10093, 10093, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10094, 10094, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10094, 10094, 10094, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10095, 10095, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10095, 10095, 10095, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10096, 10096, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10096, 10096, 10096, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10097, 10097, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10097, 10097, 10097, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10098, 10098, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10098, 10098, 10098, 50, 'EARN', CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.loyalty_accounts (id, user_id, points, stamps, tier, created_at, updated_at)
VALUES (10099, 10099, 1000, 1, 'BRONZE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO loyalty_schema.point_transactions (id, user_id, order_id, points, type, created_at)
VALUES (10099, 10099, 10099, 50, 'EARN', CURRENT_TIMESTAMP);

-- ==========================================
-- Reset sequences
-- ==========================================
\connect
user_db
SELECT setval('user_schema.user_profiles_id_seq', 11000);
\connect
auth_db
SELECT setval('auth_schema.auth_accounts_id_seq', 11000);
\connect
store_db
SELECT setval('store_schema.stores_id_seq', 11000);
\connect
locker_db
SELECT setval('locker_schema.lockers_id_seq', 11000);
\connect
order_db
SELECT setval('order_schema.orders_id_seq', 11000);
\connect
payment_db
SELECT setval('payment_schema.payments_id_seq', 11000);
\connect
iot_db
SELECT setval('iot_schema.device_statuses_id_seq', 11000);
\connect
notification_db
SELECT setval('notification_schema.notifications_id_seq', 11000);
\connect
loyalty_db
SELECT setval('loyalty_schema.loyalty_accounts_id_seq', 11000);
