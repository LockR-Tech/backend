import re

with open('seed-full-demo-ms.sql', 'r', encoding='utf-8') as f:
    content = f.read()

replacements = [
    (r"'Khach', 'Hang ' \|\| g", r"(ARRAY['Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Huỳnh', 'Phan', 'Vũ', 'Võ', 'Đặng'])[1 + (g % 10)], (ARRAY['Minh Anh', 'Thị Bé', 'Văn Chung', 'Hữu Đạt', 'Thanh Tùng', 'Thị Gái', 'Hải Hoàng', 'Văn Tuấn', 'Bảo Ngọc', 'Quốc Bảo'])[1 + (g % 10)]"),
    (r"'customer' \|\| g \|\| '@demo\.laundry\.test'", r"'khachhang' || g || '@gmail.com'"),
    (r"'Tu Demo ' \|\| g", r"'Tủ Giặt ' || g"),
    (r"'LCK-DEMO-'", r"'LCK-HCM-'"),
    (r"'Demo Store ' \|\| g", r"(ARRAY['Giặt Sấy Tiện Lợi', 'Locker Thông Minh', 'Giặt Sấy 24/7', 'Clean & Go', 'Giặt Sấy Nhanh'])[1 + (g % 5)] || ' - CN ' || g"),
    (r"'Cua hang giat say & tu khoa thong minh demo so ' \|\| g", r"'Cửa hàng giặt sấy và tủ khóa thông minh chi nhánh ' || g"),
    (r"'Tu khoa thong minh demo so ' \|\| g", r"'Tủ khóa thông minh tự phục vụ số ' || g"),
    (r"'O so ' \|\| b", r"'Ô tủ số ' || b"),
    (r"'Hong khoa dien tu'", r"'Hỏng khóa điện tử'"),
    (r"'Bao loi tu LCK-DEMO-'", r"'Báo lỗi tủ LCK-HCM-'"),
    (r"'O tu gap su co can kiem tra, ma su co demo ' \|\| g", r"'Ô tủ không mở được cửa, cần kiểm tra gấp, sự cố số ' || g"),
    (r"'Buoc xu ly demo ' \|\| g \|\| ': da kiem tra, thay the linh kien va test lai\.'", r"'Bước xử lý ' || g || ': đã đến tận nơi kiểm tra, thay thế linh kiện khóa và test lại thành công.'"),
    (r"'Bao tri dinh ky tu LCK-DEMO-'", r"'Bảo trì định kỳ tủ LCK-HCM-'"),
    (r"'DEMO-PROMO-'", r"'PROMO-HCM-'"),
    (r"'Khuyen mai demo ' \|\| g", r"'Khuyến mãi tri ân khách hàng ' || g"),
    (r"'ORD-DEMO-'", r"'ORD-HCM-'"),
    (r"'Nguyen Quoc Bao Huy'", r"'Nguyễn Quốc Bảo Huy'"),
    (r"'Ghi chu khach hang demo ' \|\| g", r"'Khách hàng gửi đồ vào buổi sáng, lấy vào buổi chiều.'"),
    (r"'Don demo so ' \|\| g", r"'Đơn hàng dịch vụ giặt sấy số ' || g"),
    (r"'Dich vu demo cho don ORD-DEMO-'", r"'Dịch vụ giặt sấy cho đơn ORD-HCM-'"),
    (r"'Khach hang tao don'", r"'Khách hàng tạo đơn'"),
    (r"'Bat dau xu ly'", r"'Bắt đầu xử lý'"),
    (r"'Danh gia demo cho don ' \|\| g \|\| ', dich vu tot\.'", r"'Dịch vụ rất tốt, lấy đồ nhanh và tủ sạch sẽ.'"),
    (r"'Khieu nai demo so ' \|\| g", r"'Đồ bị nhăn một chút nhưng dịch vụ nhìn chung ổn.'"),
    (r"'PAY-DEMO-'", r"'PAY-HCM-'"),
    (r"'TXN-DEMO-'", r"'TXN-HCM-'"),
    (r"'Thanh toan don ORD-DEMO-'", r"'Thanh toán đơn ORD-HCM-'"),
    (r"'Giao dich thanh toan demo ' \|\| g", r"'Giao dịch thanh toán tự động số ' || g"),
    (r"'Hoan tien demo cho don ' \|\| g", r"'Hoàn tiền cho đơn ' || g || ' do khách huỷ'"),
    (r"'RF-DEMO-'", r"'RF-HCM-'"),
    (r"'Thong bao demo ' \|\| g", r"'Cập nhật trạng thái đơn hàng ' || g"),
    (r"'Noi dung thong bao demo so ' \|\| g \|\| ' cho tai khoan nguoi dung\.'", r"'Đơn hàng của bạn đã được cập nhật trạng thái mới nhất.'"),
    (r"'fcm-demo-token-'", r"'fcm-token-'"),
    (r"'DEV-DEMO-'", r"'DEV-HCM-'"),
    (r"'@demo\.laundry\.test'", r"'@gmail.com'"),
    (r"'So ' \|\| g \|\| ' Nguyen Van Cu, Quan ' \|\| \(1 \+ \(g % 12\)\) \|\| ', TP\.HCM'", r"'Số ' || g || ' Nguyễn Văn Cừ, Quận ' || (1 + (g % 12)) || ', TP.HCM'"),
    (r"'Bao Huy'", r"'Bảo Huy'"),
    (r"'Quoc Bao'", r"'Quốc Bảo'"),
    (r"'Admin'", r"'Quản trị viên'"),
    (r"'Bao Tri'", r"'Bảo Trì'"),
    (r"'Quan Ly'", r"'Quản Lý'"),
    (r"'Demo Store '", r"'Cửa hàng '"),
    (r"'Tu Demo '", r"'Tủ Giặt '")
]

for pattern, repl in replacements:
    content = re.sub(pattern, repl, content)

with open('seed-real-data-ms.sql', 'w', encoding='utf-8') as f:
    f.write(content)

print("Generated seed-real-data-ms.sql")
