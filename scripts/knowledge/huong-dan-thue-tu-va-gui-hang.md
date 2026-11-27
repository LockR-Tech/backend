# Chi tiết chức năng thuê tủ và gửi hàng Lock.R

Tài liệu này giải thích đầy đủ toàn bộ tính năng, cách tính giá, thời hạn, gia hạn, uỷ quyền, báo sự cố và xử lý hàng quá hạn cho hai dịch vụ tủ khoá thông minh **Thuê tủ theo giờ (RENTAL)** và **Gửi hàng qua tủ (SEND)**.

## Thuê tủ theo giờ trên App và tại màn hình Kiosk

- **Đặt trên ứng dụng Lock.R**: Chọn tủ đang hoạt động -> chọn loại ô (**Tiêu chuẩn / STANDARD** hoặc **Lớn / XL**) -> chọn số giờ thuê (hoặc ô cụ thể) -> áp dụng voucher/điểm thưởng nếu muốn -> thanh toán VietQR.
- **Thuê trực tiếp tại màn hình Kiosk**: Khách đứng trước tủ có thể chọn thuê ô ngay trên màn hình cảm ứng của tủ mà không cần cài sẵn ứng dụng, nhập **email** để hệ thống gửi thông tin ô, mã PIN mở ô và biên lai điện tử.
- **Loại ô được phép thuê**: Chỉ các ô `STANDARD` và `XL` được phép thuê; các ô chuyên dụng cho drone (`DRONE`) không mở cho thuê thông thường.

### Cách tính giá thuê và gói trần ngày

- Giá thuê được tính theo **đơn giá giờ** của từng loại ô (`STANDARD` và `XL`), nhân với số giờ thuê bạn chọn (giới hạn giờ tối thiểu và tối đa theo cấu hình hệ thống).
- Khi bạn thuê dài giờ trong một ngày, hệ thống tự động áp dụng **giá trần theo ngày (Daily Cap)** cho mỗi chu kỳ 24 giờ nếu tổng đơn giá theo giờ vượt quá mức trần ngày của loại ô đó — giúp thuê nhiều giờ tiết kiệm hơn.
- Khi bạn bấm **Gia hạn thêm giờ**, hệ thống cộng dồn vào **tổng số giờ của cả hợp đồng** rồi lấy giá tổng mới trừ đi giá cũ, nhờ vậy việc gia hạn nối tiếp sang mốc 24 giờ vẫn được hưởng mức giá trần ngày ưu đãi.

### Quy trình cất đồ, mở nhiều lần và kết thúc thuê

1. Sau khi thanh toán thành công, đến tủ nhập mã thuê trên Kiosk hoặc bấm **Mở ô để bỏ đồ** trên ứng dụng.
2. Cất đồ vào ô và đóng kín cửa. Xác nhận đã cất đồ (`STORING`): **đồng hồ đếm giờ thuê chính thức bắt đầu chạy** từ thời điểm này (nên dù bạn đặt trước từ nhà thì thời gian thuê vẫn chỉ tính từ lúc bỏ đồ vào tủ).
3. **Mở ô nhiều lần khi đang thuê**: Trong suốt hạn thuê, bạn được mở ô không giới hạn số lần bằng cùng mã PIN thuê hoặc từ ứng dụng. Mỗi lần mở trên Kiosk, chọn **"Đóng cửa, tiếp tục thuê"** nếu vẫn giữ ô.
4. **Kết thúc thuê**: Khi lấy hết đồ ra, chọn **"Lấy hết đồ & kết thúc thuê"** trên Kiosk hoặc bấm kết thúc trên ứng dụng. Nếu không còn phí quá giờ chưa thanh toán, ô được giải phóng (`AVAILABLE`) và đơn chuyển sang **Hoàn tất (`COMPLETED`)**.

## Gia hạn thuê và phí quá giờ thuê

- **Gia hạn trước hạn**: Vào chi tiết đơn thuê -> **Gia hạn** -> chọn số giờ thêm -> thanh toán phần chênh lệch qua VietQR. Mốc hạn sử dụng được cộng nối tiếp vào hạn cũ.
- **Khi đã quá giờ thuê**:
  - Mã PIN mở ô sẽ bị **khoá tạm thời** trên Kiosk để tránh lấy đồ khi chưa thanh toán phần quá giờ.
  - Phí quá giờ của đơn thuê được tính **trọn từng giờ đã bắt đầu** (ví dụ quá 5 phút tính 1 giờ) theo đơn giá giờ loại ô nhân tỉ lệ phí quá giờ cấu hình (tối thiểu 100% giá thuê giờ, không áp dụng mức trần thấp hơn gia hạn).
  - Bạn có hai lựa chọn trong chi tiết đơn trên ứng dụng: bấm **Gia hạn** (hệ thống tự chốt các giờ đã quá cộng vào tổng đơn trước rồi mới cộng tiếp giờ thuê mới) hoặc bấm thanh toán phần phí quá giờ để ra tủ lấy hết đồ kết thúc thuê.
- **Thời gian ân hạn (Grace period)**: Sau khi bạn thanh toán xong phần phí quá giờ/gia hạn, hệ thống mở một khoảng thời gian ân hạn mặc định để bạn di chuyển tới mở tủ lấy đồ mà không bị cộng nhảy thêm giờ quá hạn mới.

## Gửi hàng qua tủ (SEND) và các tính năng hỗ trợ người nhận

### Cơ chế hai mã PIN riêng biệt

1. **Giai đoạn bỏ hàng (`INITIALIZED`)**: Sau khi thanh toán, người gửi dùng mã PIN gửi hàng (hoặc mở trên app) để bỏ kiện vào ô. Trước khi cho phép xác nhận "Đã bỏ hàng", hệ thống kiểm tra ô đã từng được mở thực tế cho đơn hàng đó.
2. **Giai đoạn chờ nhận (`STORING`)**: Ngay khi người gửi xác nhận đã bỏ hàng, **mã gửi hàng lập tức hết hiệu lực** (người gửi không tự mở lại để tránh tranh chấp), đồng thời hệ thống tạo **mã lấy hàng mới** gửi qua email người nhận và thông báo trong app. Thời hạn giữ hàng bắt đầu đếm từ lúc này.

### Gửi lại email, chia sẻ mã và đổi mã PIN

- **Email người nhận bắt buộc**: Đảm bảo mọi người nhận đều nhận được mã mở ô kể cả khi không dùng app Lock.R. Chủ đơn có thể bấm **"Gửi lại email mã nhận hàng"** trong chi tiết đơn bất kỳ lúc nào nếu người nhận chưa thấy thư.
- **Chia sẻ nhanh**: Hỗ trợ nút sao chép nội dung tin nhắn chuẩn (tên tủ, vị trí ô, mã PIN, hạn lấy) hoặc gửi qua SMS/Zalo/Messenger.
- **Đổi mã PIN (`Đổi mã mở tủ`)**: Chủ đơn (người gửi) có thể yêu cầu cấp lại mã PIN ngẫu nhiên mới khi cần bảo mật; mã cũ lập tức bị huỷ.

### Uỷ quyền cho người khác lấy hộ

- Áp dụng khi hàng đang nằm trong tủ chờ nhận (`STORING` / `RETURNED`).
- Chủ đơn nhập **số điện thoại**, **họ tên** (tuỳ chọn) và **ghi chú** cho người lấy hộ ngay trong chi tiết đơn.
- Hệ thống lập tức sinh **mã PIN mới**, vô hiệu hoá mã cũ và thông báo cho người gửi. Nếu người được uỷ quyền đã có tài khoản Lock.R trùng số điện thoại, đơn sẽ hiện trong mục **"Được uỷ quyền"** trên ứng dụng của họ để xem mã mở ô và nhận hàng tại kiosk.

### Gia hạn giữ hàng và phí quá hạn gửi hàng

- **Gia hạn giữ hàng**: Người gửi chọn số giờ gia hạn thêm trong chi tiết đơn.
  - Nếu đơn chưa thanh toán lần nào: giờ và tiền được cộng thẳng vào đơn.
  - Nếu đơn đã thanh toán ban đầu: khoản phí gia hạn được tạo ở trạng thái **chờ thanh toán** — hạn lấy hàng cũ vẫn giữ nguyên cho tới khi thanh toán VietQR cho lần gia hạn đó thành công. Nếu để sang quá giờ trước khi thanh toán gia hạn, khoản gia hạn treo sẽ bị huỷ để tính theo phí quá giờ thực tế.
- **Phí quá hạn gửi hàng**: Tính theo mỗi giờ quá hạn (theo đơn giá giờ cấu hình) nhưng được bảo vệ bởi **trần % trên giá gốc** và **mức trần tối đa (VND)** do quản trị viên thiết lập.

## Báo ô hỏng, dừng tính phí quá giờ và hàng đưa về kho

### Báo ô hỏng khi đang chứa đồ

- Khi ô bị kẹt chốt, không mở được hoặc mất điện, khách mở chi tiết đơn bấm **Báo ô hỏng** (kèm mô tả và ảnh thực tế).
- Ô chuyển sang trạng thái chờ kiểm tra (`PENDING_CHECK`), hoặc `FAULT` nếu KTV đã xác nhận hỏng.
- **Đóng băng phí quá giờ**: Đồng hồ tính phí quá hạn của đơn được **dừng lại ngay tại thời điểm báo sự cố ô**, tránh phát sinh phí oan trong lúc chờ kỹ thuật viên xử lý. Sau khi KTV hoàn tất sửa chữa, khách nhận thông báo để tiếp tục lấy đồ bình thường.
- Trong thời gian ô đang có sự cố chờ xử lý, hệ thống tạm khoá chức năng gia hạn trên đơn đó vì đồng hồ phí đã được tạm dừng.

### Hàng quá hạn đưa vào kho lưu trữ (`EXPIRED`)

- Nếu đồ gửi hoặc đồ thuê để quá thời hạn tối đa cho phép mà không có phản hồi gia hạn/lấy đồ, bộ xử lý định kỳ hoặc Admin sẽ thu hồi đồ đưa về **Kho lưu trữ trung tâm**, giải phóng ô tủ (`AVAILABLE`) và chuyển đơn sang trạng thái **Đã chuyển về kho (`EXPIRED`)**.
- Để nhận lại đồ đang lưu kho, khách hàng (người gửi, người nhận, người được uỷ quyền hoặc người nhận thay được xác nhận) tới điểm/quầy trao trả của Lock.R, cung cấp **4 số cuối số điện thoại** để đối chiếu, thanh toán phần phí còn thiếu (nếu còn công nợ) bằng tiền mặt hoặc chuyển khoản, và chụp ảnh xác nhận biên bản bàn giao để đóng đơn hoàn tất.
