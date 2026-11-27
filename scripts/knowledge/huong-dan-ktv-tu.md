# Quy trình vận hành và bảo trì tủ cho kỹ thuật viên

Tài liệu dành cho kỹ thuật viên tủ (**KTV tủ / LOCKER_TECHNICIAN**) và quản trị viên (**ADMIN**). KTV tủ phụ trách vận hành, lắp đặt, sửa chữa sự cố và bảo trì định kỳ/đột xuất cho hệ thống tủ khoá (Kiosk), ô tủ và bãi đáp drone trên nóc tủ.

## Phân công tủ và quyền xử lý

- Admin phân công một KTV tủ phụ trách chính cho từng tủ trên Web Admin (chi tiết tủ -> tab *Phụ trách & vị trí*).
- Phiếu sự cố mới của một tủ được thông báo ngay cho KTV phụ trách tủ đó. Nếu tủ **chưa phân công KTV phụ trách**, thông báo được gửi cho **toàn bộ KTV tủ** để bất kỳ KTV nào rảnh đều có thể vào nhận xử lý.
- Khi Admin thay đổi KTV phụ trách của tủ, các phiếu sự cố đang ở trạng thái chờ tiếp nhận (`OPEN`) và các lịch kiểm tra định kỳ đang gắn KTV cũ của tủ có thể được điều chuyển sang KTV mới.
- Trên ứng dụng Mobile KTV, mục **"Tủ tôi phụ trách"** hiển thị các phiếu và tủ mình quản lý trực tiếp.

## Phân biệt Phiếu sự cố / Sửa chữa và Phiếu / Kế hoạch bảo trì

Hệ thống tách bạch rõ hai loại công việc để không bị lẫn số liệu SLA và lịch sử bảo dưỡng:

- **Phiếu sự cố / Sửa chữa (`LockerReport`)**: Tạo ra khi khách hàng báo ô hỏng, KTV/Admin báo sự cố thiết bị (ô hỏng, lỗi cả tủ, hỏng bãi đáp), hoặc được hệ thống tự động sinh ra khi một lần kiểm tra bảo trì có hạng mục bị đánh dấu **Không đạt**.
- **Phiếu / Kế hoạch bảo trì (`MaintenanceSchedule`)**: Bao gồm **Bảo trì định kỳ** (lặp lại theo chu kỳ `>= 1` ngày) và **Bảo trì đột xuất 1 lần** (`intervalDays = 0`).
  - Khi Admin bấm **Bảo trì** tại danh sách Kiosk hoặc chi tiết Kiosk trên Web Admin, hoặc KTV bấm **Bảo trì** tại chi tiết tủ trên app Mobile, tủ chuyển sang trạng thái **Bảo trì (`MAINTENANCE`)** và hệ thống tự động tạo một **phiếu bảo trì 1 lần (`MaintenanceSchedule`)** — **không tạo phiếu sự cố**.
  - Phiếu bảo trì hiển thị trong mục **Kế hoạch bảo trì -> Trạm Kiosk** (`/admin/maintenance` và màn hình Bảo trì trên app Mobile) và trong tab **Sửa chữa & Bảo trì** ở chi tiết từng tủ.

## Nhận và xử lý phiếu sự cố / sửa chữa

### Tiếp nhận phiếu

- Phiếu do khách báo bắt đầu ở trạng thái **Chờ tiếp nhận (`OPEN`)**. KTV bấm **Nhận việc** để chuyển phiếu sang **Đang xử lý (`IN_PROGRESS`)** và bắt đầu tính trách nhiệm SLA trên người nhận.
- Phiếu do chính KTV tạo cho ô/tủ/bãi đáp được tự động giao luôn cho người tạo ở trạng thái đang xử lý. Admin cũng có thể chỉ định giao trực tiếp phiếu cho một KTV.
- Nếu cùng một ô đang có phiếu mở, các lần báo hỏng bổ sung cho ô đó sẽ được **gộp vào phiếu đang mở** (bổ sung ghi chú/ảnh) thay vì tạo phiếu trùng.
- **Ràng buộc SLA**: KTV đang giữ số lượng phiếu trễ hạn SLA chạm ngưỡng hạn chế do Admin cấu hình sẽ bị chặn không nhận thêm phiếu mới cho tới khi hoàn tất xử lý các phiếu trễ.

### Nhật ký xử lý và hoàn tất phiếu sự cố

1. Trong quá trình kiểm tra, sửa chữa tại hiện trường, KTV ghi nhật ký công việc và đính kèm ảnh chụp tình trạng trước/trong khi sửa.
2. Khi đã khắc phục xong, bấm **Hoàn tất** trên phiếu, nhập ghi chú nghiệm thu và chụp ảnh nghiệm thu (bắt buộc tối thiểu 1 ảnh nếu hệ thống bật cấu hình *"Bắt buộc ảnh nghiệm thu"*).
3. Chỉ KTV đang được giao phiếu hoặc Admin mới có quyền hoàn tất phiếu.
4. Khi hoàn tất phiếu sự cố, hệ thống tự động khôi phục trạng thái tài sản:
   - **Ô tủ**: trở về trạng thái trước khi hỏng (ô đang chứa hàng của đơn quay về `OCCUPIED` để khách tiếp tục mở lấy hàng; ô trống quay về `AVAILABLE`). Nếu ô có gắn đơn đang đóng băng phí quá giờ (`PENDING_CHECK`/`FAULT`), hệ thống mở khoá lại cho đơn.
   - **Bãi đáp drone**: trở về `OK`.
   - **Cả tủ**: nếu tủ bị ngưng do phiếu sự cố toàn tủ, tủ tự động hoạt động lại (`ACTIVE`) khi phiếu chặn cuối cùng được hoàn tất (nhưng nếu tủ còn đang được Admin bật bảo trì thì vẫn giữ `MAINTENANCE`).
   - Khách hàng báo hỏng nhận được thông báo hoàn tất và có thể gửi đánh giá chất lượng xử lý.

### Nút "Đã sửa" trên ô hỏng

- Nếu ô hỏng đang có phiếu sự cố mở: bấm **Đã sửa** sẽ mở thẳng màn hình hoàn tất phiếu đó (nhập ghi chú + ảnh nghiệm thu). Nếu phiếu chưa giao cho bạn, hệ thống nhắc nhận phiếu trước rồi mới hoàn tất.
- Nếu ô hỏng không có phiếu sự cố nào gắn kèm: bấm **Đã sửa** đưa ô về trạng thái trước khi hỏng ngay lập tức.

## Thực hiện phiếu bảo trì định kỳ và bảo trì đột xuất

- Trong mục **Bảo trì / Định kỳ** trên app Mobile (dùng bộ lọc *"Của tôi"*), KTV thấy danh sách các kế hoạch định kỳ và các phiếu bảo trì đột xuất 1 lần (`intervalDays = 0`). Chỉ KTV được phân công (hoặc tủ chưa phân công nếu mở quyền phù hợp) hoặc Admin mới ghi kết quả kiểm tra.
- Khi kiểm tra, KTV đánh giá **đủ tất cả các mục checklist**: `ĐẠT`, `KHÔNG ĐẠT` hoặc `KHÔNG ÁP DỤNG`, kèm ghi chú và ảnh hiện trường.
- **Nếu tất cả các mục đều ĐẠT hoặc KHÔNG ÁP DỤNG (Kết quả ĐẠT)**:
  - Với **lịch định kỳ** (`intervalDays >= 1`): hạn kiểm tra kế tiếp tự động dời thêm một chu kỳ.
  - Với **phiếu bảo trì đột xuất 1 lần** (`intervalDays = 0`): phiếu tự động đóng sau khi hoàn tất; nếu tủ đang ở trạng thái `MAINTENANCE` và không còn phiếu bảo trì 1 lần nào khác đang mở, tủ tự động khôi phục về **Hoạt động (`ACTIVE`)**.
- **Nếu có ít nhất 1 mục KHÔNG ĐẠT (Kết quả KHÔNG ĐẠT)**:
  - Hệ thống tự động mở một **Phiếu sự cố (`LockerReport`)** gắn trực tiếp với kế hoạch bảo trì đó và giao cho KTV vừa kiểm tra; các ô bị đánh dấu hỏng chuyển ngay sang `FAULT`.
  - Lịch/phiếu bảo trì chuyển sang trạng thái chờ sửa phiếu sự cố (`chờ phiếu RPT-...`), không ghi được lần kiểm tra mới và chưa dời hạn / chưa tự mở lại tủ cho tới khi phiếu sự cố đó được hoàn tất xong.
- Mỗi sáng lúc **07:00**, hệ thống tự động gửi thông báo nhắc các kế hoạch bảo trì sắp tới hạn hoặc đã tới hạn (mỗi kỳ hạn chỉ nhắc một lần).

## Báo sự cố cả tủ, bãi đáp drone, tạm ngưng ô và mở khẩn cấp

### Báo sự cố cả tủ

- Khi KTV hoặc Admin tạo phiếu sự cố cấp toàn tủ ở chế độ **ngưng tủ** (cúp điện tổng, hỏng bo mạch chính, hỏng màn hình Kiosk), tủ lập tức chuyển sang trạng thái ngừng nhận đơn và giữ cho tới khi phiếu được hoàn tất.
- Tủ đang bảo trì hoặc vô hiệu không nhận đặt đơn gửi, thuê ô, cũng như không nhận giao hàng drone.

### Bãi đáp drone trên nóc tủ

- KTV tủ cập nhật trạng thái bãi đáp: `OK`, `FAULT` (Hỏng) hoặc `MAINTENANCE` (Bảo trì) kèm lý do.
- Chuyển bãi đáp sang `FAULT` hoặc `MAINTENANCE` sẽ mở một phiếu bãi đáp (mỗi bãi đáp chỉ có 1 phiếu mở tại một thời điểm) và giao cho người báo. Chuyển lại `OK` khi đang có phiếu đồng nghĩa với hoàn tất phiếu đó.

### Ô tạm ngưng, vệ sinh và mở ô khẩn cấp

- **Tạm ngưng / Vệ sinh**: Chỉ áp dụng được cho ô **đang trống** (`AVAILABLE`); chuyển sang `DISABLED` (tạm ngưng dùng, bắt buộc nhập lý do) hoặc `CLEANING` (đang vệ sinh). Ô đang giữ hàng không được thao tác mục này. Khôi phục bằng nút *Khôi phục*.
- **Mở ô khẩn cấp**: Mở khoá điện từ của ô mà không cần mã PIN khách hàng (chỉ dùng khi hỗ trợ khách bị kẹt đồ hoặc xử lý sự cố). Mọi lượt mở khẩn cấp đều ghi lại nhật ký kiểm toán (`Audit Log`) gồm người thực hiện và thời điểm.

## Quy trình lắp đặt và nghiệm thu tủ mới

Khi triển khai một tủ mới trên thực địa, quy trình hồ sơ lắp đặt đi qua 4 giai đoạn trạng thái (`SCHEDULED` -> `IN_PROGRESS` -> `SUBMITTED` -> `APPROVED`), tương ứng trạng thái tủ `PLANNED` -> `INSTALLING` -> `COMMISSIONING` -> `ACTIVE`:

1. **Lập hồ sơ lắp đặt (Admin)**: Admin tạo hồ sơ chọn tủ, ngày hẹn, KTV lắp đặt, ghi chú hiện trường và phần cứng -> tủ chuyển sang `PLANNED` (khách chưa thấy).
2. **Thi công và kiểm tra 8 hạng mục (KTV)**: KTV bấm bắt đầu lắp đặt (`IN_PROGRESS` / tủ `INSTALLING`) và thực hiện 8 bước checklist:
   - `Lắp đặt cố định & cấp điện` (KTV xác nhận tay + bắt buộc ảnh)
   - `Kết nối mạng (bộ điều khiển online)` (Hệ thống tự xác nhận qua IoT)
   - `Gán bộ điều khiển & cài sơ đồ ô` (Hệ thống tự xác nhận qua IoT)
   - `Áp cấu hình phần cứng` (Hệ thống tự xác nhận qua IoT)
   - `Mở thử từng ô` (Hệ thống tự xác nhận qua IoT)
   - `Thử nắp trượt / bãi đáp` (Hệ thống tự xác nhận qua IoT, cho phép Không áp dụng với tủ không có bãi đáp)
   - `Màn hình kiosk hoạt động` (KTV xác nhận tay + bắt buộc ảnh)
   - `Mở ô bằng Bluetooth` (KTV xác nhận tay, cho phép Không áp dụng nếu chưa kích hoạt BLE)
3. **Chụp ảnh hiện trường và gửi duyệt**: KTV tải lên tối thiểu **2 ảnh hiện trường chung** (ngoài ảnh theo bước), đảm bảo mọi mục hợp lệ đã đạt rồi bấm **Gửi duyệt nghiệm thu (`SUBMITTED`)**, tủ chuyển sang `COMMISSIONING`.
4. **Admin nghiệm thu**: Admin kiểm tra ảnh và kết quả tự động, bấm **Duyệt kích hoạt** -> tủ chuyển sang **Hoạt động (`ACTIVE`)** sẵn sàng nhận đơn (hoặc **Trả lại** KTV sửa bổ sung nếu chưa đạt, hoặc Huỷ hồ sơ). Thông tin hồ sơ và lịch sử lắp đặt luôn xem lại được trong tab **Lắp đặt** của chi tiết tủ trên Web Admin và Mobile KTV.

## SLA và chế tài

- Mỗi phiếu sự cố có **hạn xử lý SLA** tính theo giờ kể từ khi tạo phiếu (thời hạn do Admin cấu hình tại Cài đặt hệ thống). Phiếu quá hạn không hoàn tất bị đánh dấu trễ hạn SLA.
- KTV có thể gửi yêu cầu **gia hạn SLA** kèm lý do chính đáng; mọi lần gia hạn đều lưu vào nhật ký phiếu.
- Khi số lượng phiếu trễ hạn SLA vượt các ngưỡng cấu hình, KTV sẽ áp dụng các mức chế tài tăng dần: **Cảnh báo**, **Hạn chế nhận việc mới**, và **Đình chỉ**. Mức hiệu suất, điểm đánh giá của khách và số phiếu trễ hạn hiển thị trong hồ sơ KTV.

## Thông báo KTV nhận được

- Có phiếu sự cố mới tại tủ mình phụ trách (hoặc toàn bộ tủ chưa có người phụ trách).
- Được Admin giao trực tiếp một phiếu sự cố hoặc phân công phụ trách một tủ mới.
- Nhắc lịch bảo trì định kỳ sắp tới hạn / quá hạn lúc 07:00 hàng ngày, hoặc khi có phiếu bảo trì 1 lần mới được tạo.
- Kết quả duyệt / trả lại hồ sơ lắp đặt tủ mới.
