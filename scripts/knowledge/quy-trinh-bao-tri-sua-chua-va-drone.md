# Quy trình bảo trì, sửa chữa Kiosk/Drone và xử lý sự cố vận hành

Tài liệu nội bộ dành cho kỹ thuật viên tủ (**LOCKER_TECHNICIAN**), kỹ thuật viên drone (**DRONE_TECHNICIAN**) và quản trị viên (**ADMIN**), chuẩn hoá cách phân biệt **Bảo trì (`MaintenanceSchedule`)** với **Sửa chữa / Sự cố (`LockerReport`)** trên cả Web Admin và Mobile KTV, cũng như quy trình vận hành & xử lý sự cố đội bay Drone.

## Nguyên tắc phân biệt Bảo trì và Sửa chữa trên toàn hệ thống

- **Bảo trì (`MaintenanceSchedule`)**: Áp dụng cho cả kiểm tra **định kỳ theo chu kỳ** (`intervalDays >= 1`) và **bảo trì đột xuất 1 lần** (`intervalDays = 0`). Dùng để kiểm tra sức khoẻ thiết bị, bảo dưỡng phòng ngừa theo checklist.
- **Sửa chữa / Sự cố (`LockerReport`)**: Áp dụng khi thiết bị bị lỗi thực tế cần sửa chữa (khách báo hỏng ô, KTV/Admin báo sự cố, hoặc phát sinh khi một hạng mục kiểm tra bảo trì bị đánh dấu **Không đạt**). Có mã phiếu dạng `RPT-...`, tính hạn SLA và phân bổ trực tiếp KTV xử lý.

### Hành vi chuẩn khi thao tác "Bảo trì" vs "Báo sự cố / Hỏng"

- **Tại Web Admin (`/admin/lockers`, `/admin/lockers/{id}`, `/admin/drones`, `/admin/drones/{id}`) và Mobile KTV Tủ / Mobile KTV Drone**:
  - Bấm **Bảo trì** (chuyển Kiosk hoặc Drone sang trạng thái `MAINTENANCE`): hệ thống **tạo phiếu bảo trì 1 lần (`MaintenanceSchedule`, `intervalDays = 0`)** cho thiết bị tương ứng, gắn KTV phụ trách nếu có — **không tạo phiếu sự cố (`LockerReport`)**.
  - Bấm **Hỏng / Báo sự cố** (hoặc chuyển Drone sang `FAULT`): hệ thống tạo **phiếu sự cố (`LockerReport`)** với phân loại `LOCKER`, `BOX`, `LANDING_PAD` hoặc `DRONE`.
- **Đồng bộ giao diện xem lịch sử**:
  - Trang **/admin/maintenance** hiển thị hai tab tách biệt: **Kế hoạch Kiosk** và **Kế hoạch Drone** (gồm cả lịch định kỳ lẫn phiếu bảo trì 1 lần).
  - Trong chi tiết Kiosk (`/admin/lockers/{id}`) và chi tiết Drone (`/admin/drones/{id}`), tab **Sửa chữa & Bảo trì** hiển thị gộp cả danh sách **Phiếu sự cố** và **Lịch sử kiểm tra / bảo trì**.

## Quy trình bảo trì và sửa chữa cho KTV Drone (`DRONE_TECHNICIAN`)

### Quản lý đội bay và phân công Drone

- Mỗi drone có trạng thái hoạt động: `ACTIVE` (Sẵn sàng), `IN_FLIGHT` (Đang bay), `MAINTENANCE` (Đang bảo trì), `FAULT` (Đang hỏng), hoặc `OFFLINE`.
- Admin phân công KTV Drone phụ trách từng chiếc drone hoặc KTV chủ động theo dõi đội bay tại trạm mình quản lý trên ứng dụng Mobile KTV Drone.

### Bảo trì Drone (`MaintenanceSchedule`)

1. Khi Admin hoặc KTV Drone chuyển drone sang **Bảo trì**, một phiếu bảo trì 1 lần (`intervalDays = 0`) được mở ngay trong danh sách **Bảo trì (Đội bay Drone)**. Các lịch bảo trì định kỳ (kiểm tra cánh quạt, pin, cảm biến, hook/gripper, mạch Pixhawk, Raspberry Pi) cũng hiển thị cùng màn hình.
2. KTV Drone kiểm tra theo danh sách hạng mục (checklist) và ghi nhận kết quả từng mục: `ĐẠT`, `KHÔNG ĐẠT`, hoặc `KHÔNG ÁP DỤNG`, kèm ghi chú và ảnh kiểm tra.
3. **Nếu toàn bộ hạng mục ĐẠT (hoặc Không áp dụng)**:
   - Phiếu bảo trì định kỳ tự động dời hạn sang chu kỳ mới.
   - Phiếu bảo trì 1 lần tự đóng; nếu drone đang ở `MAINTENANCE` và không còn phiếu bảo trì 1 lần nào đang mở, hệ thống tự động đưa drone trở về trạng thái **Hoạt động (`ACTIVE`)**.
4. **Nếu có hạng mục KHÔNG ĐẠT**:
   - Hệ thống tự động sinh một **Phiếu sự cố Drone (`LockerReport`, type `DRONE`)** liên kết với kế hoạch bảo trì đó và giao cho KTV vừa kiểm tra.
   - Drone chưa tự mở lại về `ACTIVE`; KTV sửa chữa xong phần lỗi và hoàn tất phiếu sự cố `RPT-...` thì hạn bảo trì mới được giải phóng và cập nhật.

### Sửa chữa Drone qua Phiếu sự cố (`LockerReport` - type `DRONE`)

- Khi drone gặp lỗi phần cứng, hạ cánh lỗi, sự cố cơ cấu nhả hàng (`hook/gripper/servo/actuator`) hoặc không đạt bảo trì, phiếu sự cố được tạo cho drone.
- KTV Drone bấm **Nhận việc**, ghi nhật ký sửa chữa + ảnh kiểm tra linh kiện, thay thế/hiệu chỉnh thiết bị, sau đó bấm **Hoàn tất** (kèm ảnh nghiệm thu nếu bật yêu cầu).
- Khi phiếu sự cố cuối cùng của drone hoàn tất, drone (nếu không còn ở trạng thái bảo trì chủ động khác) được khôi phục sẵn sàng nhận chuyến bay.

## Quy trình giao hàng bằng Drone và xử lý sự cố rơi kiện

### 1. Lấy hàng tận nhà và nạp hàng vào Drone

- KTV Drone nhận lời mời lấy hàng tận nhà (nếu từ chối hoặc để quá hạn lời mời, hệ thống chờ hết thời gian cooldown mặc định `10 phút` mới mời lại cùng đơn).
- Khi bấm **Đã lấy hàng**, hệ thống kiểm tra GPS ca trực của KTV phải nằm trong bán kính tối đa **300 mét** quanh điểm lấy hàng.
- KTV kiểm tra kích thước thực tế (chuẩn tối đa mặc định `30 × 25 × 20 cm`), cân nặng và giá trị khai báo (tối đa `2.000.000 đ`). Nếu phát sinh phụ phí chênh lệch cân nặng, chờ khách thanh toán phụ phí (tối đa `60 phút`) trước khi cho phép nạp và phóng.

### 2. Kiểm tra thời tiết và phóng Drone

- Trước khi phóng, hệ thống kiểm tra dữ liệu thời tiết trên toàn tuyến trong cửa sổ `30 phút` (mặc định: gió ≤ `30 km/h`, gió giật ≤ `40 km/h`, tầm nhìn ≥ `1.500 m`, lượng mưa = `0 mm`, chặn mã WMO mưa/dông `51–99`).
- Mỗi `5 phút`, hệ thống tự rà soát thời tiết tại các trạm có bãi đáp: trạm vượt ngưỡng bị tự động tạm dừng (`Thời tiết`) và tự mở lại khi trời đạt điều kiện an toàn. Lệnh dừng thủ công do Admin tạo không bị tự động mở lại.
- Khi drone tới tủ nhận và mở nắp trượt thành công, hàng được thả vào ô nhận (`DRONE` -> chuyển `STORING`), nắp trượt đóng xong thì cấp mã lấy hàng cho người nhận.

### 3. Xử lý sự cố rơi kiện / hạ cánh khẩn cấp (`DroneParcelIncident`)

- Khi chuyến bay ở trạng thái `LAUNCHING`, `DEPARTED`, `EN_ROUTE`, `APPROACHING` hoặc `ARRIVED` gặp sự cố rơi kiện hoặc mất kiểm soát:
  1. KTV / Hệ thống báo sự cố rơi kiện, ghi nhận toạ độ cuối, snapshot camera và trạng thái pin.
  2. Nếu pin drone còn từ **15%** trở lên (`app.order.drone-return-min-battery`), KTV có thể ra lệnh drone quay về tủ gửi; nếu pin thấp hơn, drone tự hạ cánh an toàn theo autopilot.
  3. KTV Drone được điều phối nhận nhiệm vụ thu hồi (`ACCEPT` -> `START_SEARCHING`) di chuyển tới vị trí rơi, tìm kiếm và gửi kết quả (`FOUND`, `NOT_FOUND`, hoặc `UNSAFE`) kèm mô tả tình trạng kiện hàng + ảnh hiện trường.
  4. KTV mang kiện tìm thấy bàn giao về trạm bay / Hub (`confirmHubHandover`); Admin kiểm tra xác nhận đã về kho (`verifyRecovery`) trước khi lập phương án bồi thường hoặc giao lại cho khách hàng.
