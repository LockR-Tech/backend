# Hướng dẫn sử dụng Lock.R cho khách hàng

Lock.R là hệ thống tủ khoá thông minh hỗ trợ ba dịch vụ chính: **Gửi hàng qua tủ (SEND)**, **Thuê ô tủ theo giờ (RENTAL)** và **Giao hàng bằng drone (DRONE_DELIVERY)**. Mọi thao tác đặt đơn, thanh toán và theo dõi đơn được thực hiện trên ứng dụng Lock.R; bạn có thể mở ô tại màn hình kiosk của tủ bằng mã PIN/QR hoặc mở trực tiếp từ ứng dụng (hỗ trợ cả Bluetooth khi tủ bật tính năng).

## Đăng nhập và đăng ký

- Đăng nhập bằng **số điện thoại**: nhập số di động Việt Nam, nhận mã OTP 6 số qua tin nhắn SMS rồi nhập mã. Nếu chưa có tài khoản, hệ thống tự tạo tài khoản mới và hỏi họ tên ở bước "Hoàn tất đăng ký". Không nhận được mã thì chờ hết đếm ngược rồi bấm "Gửi lại mã".
- Đăng nhập bằng **email**: nhập email để nhận mã OTP 6 số qua hộp thư điện tử.
- Đăng nhập bằng **tài khoản Google**: sử dụng tài khoản Google sẵn có để đăng nhập nhanh.

## Phí và thời hạn

Phí gửi hàng, giá thuê ô theo giờ (ô Tiêu chuẩn và ô XL), gói giá trần theo ngày, số giờ tối thiểu/tối đa, thời hạn giữ hàng, phí quá giờ và phí giao hàng bằng drone do Lock.R cấu hình theo thời điểm và luôn hiển thị rõ trên màn hình xác nhận trước khi bạn thanh toán. Đơn đã tạo thành công sẽ giữ nguyên mức giá và thời hạn đã áp dụng lúc tạo đơn.

## Thanh toán

- Mọi đơn hàng và giao dịch phát sinh (gửi hàng, thuê ô, gia hạn, phí quá hạn, giao bằng drone, phụ phí cân nặng) chỉ thanh toán bằng **chuyển khoản ngân hàng qua mã VietQR (SePay)**.
- Hệ thống **không nhận tiền mặt**, **không còn ví điện tử trong ứng dụng**, và **không dùng MoMo hay VNPay**.
- Nếu đơn có áp dụng **mã giảm giá (voucher)** hoặc **điểm thưởng** làm tổng tiền phải trả về `0 đ`, đơn sẽ được xác nhận thanh toán ngay lập tức mà không cần chuyển khoản.

### Cách thanh toán qua VietQR

1. Sau khi tạo đơn hoặc gia hạn, ứng dụng hiển thị mã VietQR cùng số tài khoản, số tiền chính xác và nội dung chuyển khoản (có nút "Sao chép").
2. Quét mã QR bằng ứng dụng ngân hàng (giữ nguyên số tiền và nội dung chuyển khoản) hoặc bấm "Thanh toán bằng app ngân hàng" để mở nhanh ứng dụng ngân hàng với thông tin điền sẵn.
3. Chuyển khoản xong, quay lại ứng dụng: đơn tự động chuyển sang "Đã thanh toán" sau vài giây, hoặc bấm "Tôi đã chuyển khoản — Kiểm tra ngay".
4. Nếu đã bị trừ tiền nhưng sau vài phút đơn chưa cập nhật "Đã thanh toán", hãy làm mới trang chi tiết đơn hoặc liên hệ bộ phận hỗ trợ; **không tự ý chuyển khoản lại lần thứ hai**.

## Gửi hàng qua tủ

1. Trong ứng dụng, chọn **Gửi hàng**, chọn tủ, kích thước/loại ô (mặc định ô Tiêu chuẩn) và thời gian giữ hàng mong muốn.
2. Nhập thông tin người nhận: **số điện thoại**, **họ tên** và **email người nhận** (bắt buộc để hệ thống luôn gửi được mã nhận hàng, kể cả khi người nhận chưa có tài khoản Lock.R).
3. Thanh toán đơn hàng bằng VietQR. Mã mở ô chỉ sử dụng được sau khi đơn đã thanh toán thành công.
4. Đến tủ đã chọn, mở ô bằng cách bấm "Mở ô để bỏ hàng" trong ứng dụng hoặc nhập mã PIN gửi hàng trên màn hình kiosk, đặt hàng vào ô và đóng kín cửa.
5. Bấm **"Tôi đã bỏ hàng vào ô"** trong ứng dụng hoặc chọn **"Đã bỏ hàng xong"** trên kiosk. Hệ thống yêu cầu ô phải được mở thực tế ít nhất một lần mới cho xác nhận.

### Gửi mã lấy hàng và theo dõi nhận hàng

- Ngay sau khi xác nhận đã bỏ hàng, mã PIN gửi hàng cũ bị vô hiệu hoá (người gửi không còn tự mở lại ô đó) và hệ thống sinh **mã lấy hàng mới** cho người nhận.
- Bạn có thể bấm "Chia sẻ mã cho người nhận" (Zalo, Messenger, SMS), "Sao chép tin nhắn" hoặc gửi lại email thông báo cho người nhận từ chi tiết đơn.
- Nếu người nhận có tài khoản Lock.R trùng số điện thoại, đơn tự hiện trong mục **"Hàng chờ nhận"** kèm thông báo trong ứng dụng.
- Đơn đã tạo nhưng quá hạn không bỏ hàng vào ô sẽ tự động huỷ và giải phóng ô.

### Gia hạn giữ hàng và uỷ quyền lấy hộ

- **Gia hạn giữ hàng**: Trước khi hết hạn giữ hàng (hoặc khi cần thêm thời gian), chủ đơn có thể chọn gia hạn ngay trong chi tiết đơn. Với đơn đã thanh toán trước đó, phần phí gia hạn được tạo thành khoản chờ thanh toán riêng — chỉ khi bạn thanh toán xong thì hạn lấy hàng mới được cộng thêm.
- **Uỷ quyền lấy hộ**: Khi hàng đang nằm trong tủ (`STORING` / `RETURNED`), chủ đơn có thể uỷ quyền cho người khác lấy hộ bằng cách nhập số điện thoại, tên và ghi chú. Hệ thống tự tạo mã PIN mới cho người được uỷ quyền, vô hiệu hoá mã cũ và hiển thị đơn trong mục **"Được uỷ quyền"** của người nhận hộ (nếu có tài khoản).
- **Đổi mã PIN**: Chủ đơn có thể tạo lại mã mở ô mới trong chi tiết đơn nếu nghi ngờ lộ mã cũ.

## Nhận hàng tại tủ

1. Lấy mã lấy hàng từ email, tin nhắn người gửi chia sẻ, hoặc mục **"Hàng chờ nhận"** / **"Được uỷ quyền"** trên ứng dụng Lock.R.
2. Tại tủ, nhập mã lấy hàng trên màn hình kiosk hoặc quét mã QR. Nếu đơn có phát sinh phí quá giờ, hoàn tất thanh toán phí trên ứng dụng trước khi mở ô.
3. Cửa ô tự mở, lấy hết hàng và đóng kín cửa ô; đơn chuyển sang trạng thái **Hoàn tất**.

## Thuê ô theo giờ

1. Trong ứng dụng (hoặc thao tác trực tiếp tại màn hình kiosk bằng email để nhận mã mở ô), chọn **Thuê tủ**, chọn tủ, loại ô (**Tiêu chuẩn** hoặc **XL**) và số giờ thuê.
2. Thanh toán đơn hàng. Thời gian thuê bắt đầu tính từ lúc bạn xác nhận đã cất đồ vào ô (mốc `STORING`).
3. Mở ô bằng ứng dụng hoặc mã thuê trên kiosk, cất đồ, đóng cửa và xác nhận.
4. Trong suốt thời hạn thuê, bạn có thể **mở lại ô nhiều lần** bằng cùng một mã thuê hoặc nút mở ô trong app. Khi mở tại kiosk, chọn **"Đóng cửa, tiếp tục thuê"** nếu vẫn giữ ô.
5. Khi không dùng nữa, lấy hết đồ ra khỏi ô và chọn **"Lấy hết đồ & kết thúc thuê"** trên kiosk hoặc xác nhận kết thúc thuê trong ứng dụng để giải phóng ô và hoàn tất đơn.

### Gia hạn thuê và phí quá giờ

- Bạn có thể bấm **Gia hạn** bất kỳ lúc nào (trước hoặc sau khi hết giờ thuê) ngay trong chi tiết đơn và thanh toán phần phí phát sinh qua VietQR.
- Khi hết giờ thuê, mã mở ô sẽ tạm khoá không mở được cửa cho tới khi bạn gia hạn hoặc thanh toán phí quá giờ trong ứng dụng.
- Nếu gia hạn khi đã ở trạng thái quá giờ, hệ thống chốt phí các giờ đã quá trước rồi mới cộng tiếp số giờ gia hạn mới tính từ mốc đó.
- Nếu hàng/đồ lưu lại vượt quá ngưỡng thời gian tối đa của tủ, hệ thống sẽ chuyển đơn sang lưu kho (`EXPIRED`) và giải phóng ô; bạn cần liên hệ quầy hỗ trợ/Admin để nhận lại tại kho.

## Khi mã không mở được ô

Màn hình kiosk sẽ thông báo nguyên nhân cụ thể:

- **Đơn chưa thanh toán (hoặc chưa thanh toán phí gia hạn/quá giờ)**: hoàn tất chuyển khoản VietQR trong ứng dụng rồi thử lại. Thao tác này không bị tính là nhập sai mã.
- **Hết thời gian thuê / quá hạn lấy hàng**: mở đơn trong ứng dụng để gia hạn hoặc thanh toán phí quá giờ, sau đó mở lại ô.
- **Sai mã PIN**: kiểm tra lại mã mới nhất (lưu ý nếu chủ đơn vừa đổi PIN hoặc uỷ quyền lấy hộ thì mã cũ đã mất hiệu lực). Nhập sai liên tiếp nhiều lần, ô sẽ tự động tạm khoá trong ít phút để bảo vệ an toàn.

## Báo ô hỏng hoặc sự cố tại tủ

- Nếu ô không mở, kẹt cửa, mất nguồn hoặc hỏng chốt, mở đơn trong ứng dụng và chọn **Báo ô hỏng**, nhập mô tả sự cố và đính kèm ảnh chụp thực tế.
- Bạn cũng có thể báo cáo sự cố chung của cả tủ (màn hình kiosk liệt, cúp điện, hư kết nối) tại trang thông tin tủ.
- Khi bạn báo ô của đơn đang chứa hàng gặp sự cố, ô chuyển sang trạng thái chờ kiểm tra (`PENDING_CHECK`), **đồng hồ tính phí quá giờ được tạm dừng ngay tại thời điểm báo sự cố** và kỹ thuật viên phụ trách tủ nhận thông báo ngay lập tức.
- Nếu ô hỏng trước khi bạn bỏ hàng vào ô (đơn chưa cất đồ), hệ thống tự huỷ đơn để hoàn tiền/đặt lại ô khác.
- Nếu ô đã chứa hàng của bạn, đồ vẫn được bảo vệ an toàn bên trong; sau khi kỹ thuật viên sửa xong và nghiệm thu, bạn nhận thông báo để ra tủ lấy hàng bình thường. Bạn có thể đánh giá chất lượng xử lý trong mục báo cáo của tôi.

## Tủ đang bảo trì

Tủ đang ở trạng thái **Bảo trì** hoặc tạm ngưng hoạt động sẽ không nhận tạo đơn mới, kể cả đơn gửi, thuê ô hoặc nhận hàng drone, và hiển thị không có ô trống. Vui lòng chọn tủ hoạt động gần nhất hoặc quay lại sau khi tủ hoàn tất bảo trì.

## Giao hàng bằng drone

- Dịch vụ **Giao hàng bằng drone** cho phép gửi kiện giữa các trạm tủ có bãi đáp drone (kích thước, cân nặng và giá trị khai báo tối đa theo quy định hiện hành hiển thị khi đặt đơn).
- Hệ thống kiểm tra thời tiết thực tế (gió, gió giật, tầm nhìn, mưa) trước khi đặt đơn và trước khi phóng drone. Khi thời tiết xấu vượt ngưỡng an toàn, trạm sẽ tự tạm dừng bay và tự mở lại khi trời an toàn.
- Nếu kiện hàng thực tế lớn/nặng hơn khai báo khi KTV tới lấy, hệ thống tính phụ phí chênh lệch để bạn duyệt thanh toán trước khi bay; nếu bạn từ chối hoặc quá hạn không thanh toán phụ phí, đơn được huỷ và hoàn lại phần tiền đã trả ban đầu.
- Trường hợp hiếm gặp xảy ra sự cố rơi kiện/hạ cánh khẩn cấp, Admin sẽ lập phương án xử lý (giao lại miễn phí, bồi thường hoặc hoàn phí giao) gửi bạn xem và phản hồi ngay trong ứng dụng.

## Huỷ đơn và hoàn tiền

### Khi nào đơn được huỷ và hoàn tiền

- **Đơn chưa thanh toán**: bạn có thể tự huỷ trực tiếp trong ứng dụng bất kỳ lúc nào trước khi thanh toán.
- **Hoàn tiền tự động sang chờ xử lý (`REFUND_PENDING`)**:
  - Đơn Gửi hàng hoặc Thuê tủ đã thanh toán nhưng chưa bỏ đồ vào ô (`INITIALIZED`) mà bạn huỷ, hoặc quá hạn bỏ đồ nên hệ thống tự huỷ, hoặc tiền chuyển khoản VietQR vào chậm sau khi đơn đã huỷ.
  - Đơn Giao hàng Drone đã thanh toán nhưng bị huỷ trước khi KTV lấy hàng, quá hạn điều phối đội bay, lỗi phần cứng/thời tiết buộc huỷ bay, hoặc từ chối/quá hạn phụ phí thay đổi cân nặng.
- **Khách chủ động gửi yêu cầu hoàn tiền**: áp dụng cho đơn đã thanh toán phát sinh sự cố hợp lệ (tủ lỗi không mở được ô và không có ô thay thế, thanh toán trùng 2 lần cùng đơn, tính sai phí quá hạn…) trong vòng **7 ngày** kể từ lúc thanh toán (giá trị hoàn tối thiểu `1.000 đ`).
- **Đơn `0 đ`** thanh toán hoàn toàn bằng voucher/điểm thưởng không phát sinh khoản tiền thực thu để hoàn tiền mặt.

### Quy trình nhận hoàn tiền về tài khoản ngân hàng

1. Vào **Hồ sơ / Tài khoản ngân hàng nhận hoàn tiền** trong ứng dụng để lưu sẵn: Tên ngân hàng, Mã ngân hàng, Số tài khoản và Tên chủ tài khoản. Nếu chưa lưu tài khoản khi hệ thống tự động tạo phiếu hoàn, ứng dụng sẽ gửi thông báo nhắc bạn cập nhật để Admin có thông tin chuyển khoản.
2. Nếu tự gửi yêu cầu, vào **Chi tiết đơn hàng -> Yêu cầu hoàn tiền**, chọn lý do hợp lệ, kiểm tra số tiền hoàn, thông tin tài khoản nhận và đính kèm ảnh minh chứng (nếu có).
3. Theo dõi trạng thái phiếu tại mục **Yêu cầu hoàn tiền** (`Đang chờ xử lý`, `Đã hoàn tiền`, hoặc `Từ chối`).
4. Quản trị viên đối chiếu giao dịch, chuyển khoản qua VietQR và tải lên ảnh minh chứng chuyển khoản. Hệ thống cập nhật trạng thái **Đã hoàn tiền** và gửi thông báo kèm ảnh đối soát cho bạn.

Lưu ý: Nếu đơn đã hoàn tất từng được cộng điểm thưởng mà sau đó phát sinh hoàn tiền, hệ thống sẽ tự động thu hồi lại số điểm đã tích tương ứng theo tỉ lệ số tiền được hoàn.

## Điểm thưởng và hạng thành viên

- Khi đơn có thực thu tiền hoàn tất (`COMPLETED`), bạn tích **1 điểm cho mỗi 10 đ** đã thanh toán (mặc định). Tổng điểm tích luỹ quyết định các hạng Bạc, Vàng, Bạch kim; việc tiêu điểm không làm tụt hạng.
- Bạn có thể dùng điểm để giảm trực tiếp tối đa một phần giá trị đơn mới hoặc đổi lấy voucher ưu đãi trong mục **Đổi điểm lấy voucher**. Khi đơn huỷ hợp lệ trước lúc hoàn tất, phần điểm đã khấu trừ cho đơn đó được hoàn lại vào tài khoản.

## Trợ lý hỏi đáp

Trợ lý trong ứng dụng trả lời dựa trên tài liệu chính thức của Lock.R và trích dẫn rõ nguồn. Bạn có thể gõ tiếng Việt có dấu hoặc không dấu (ví dụ: `"lam sao de gui hang qua tu?"`).

### Hỏi về đơn hàng của chính bạn

- Trợ lý tra cứu được trạng thái đơn của tài khoản đang đăng nhập ngay tại thời điểm hỏi: đơn đã có người lấy chưa, trạng thái thanh toán, hạn lấy hàng, thời gian thuê còn lại, vị trí chặng bay drone hoặc hàng người khác đang gửi cho bạn.
- Trợ lý **không bao giờ hiển thị mã PIN, mã QR hay mã mở ô** vì lý do bảo mật. Để lấy mã mở ô, vui lòng mở mục **Đơn hàng -> Đơn của tôi** (hoặc **Hàng chờ nhận** / **Được uỷ quyền**).
