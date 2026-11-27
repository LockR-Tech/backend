# Quy trình và điều kiện hoàn tiền Lock.R

Tài liệu này mô tả chi tiết các điều kiện được hoàn tiền, mức hoàn áp dụng, các trường hợp không đủ điều kiện và quy trình từng bước để khách hàng nhận tiền hoàn về tài khoản ngân hàng trên hệ thống Lock.R.

## Nguyên tắc chung và giới hạn hoàn tiền

- **Cổng thanh toán hỗ trợ hoàn**: Do mọi thanh toán trên Lock.R đều qua chuyển khoản **VietQR (SePay)**, tiền hoàn sẽ được chuyển khoản trực tiếp vào **tài khoản ngân hàng Việt Nam** do khách hàng cung cấp. Hệ thống không hoàn về ví điện tử trung gian hay tiền mặt.
- **Số tiền hoàn tối thiểu**: Mặc định từ **1.000 đ** trở lên (theo cấu hình `app.payment.refund-min-amount`).
- **Số tiền hoàn tối đa trên mỗi đơn**: Không vượt quá tổng số tiền khách **đã thực trả thành công** qua chuyển khoản, trừ đi tổng số tiền đã hoàn hoặc đang chờ hoàn trước đó của cùng đơn hàng (chưa tính khoản bồi thường sự cố drone nếu có).
- **Thời hạn gửi yêu cầu hoàn tiền**: Mặc định trong vòng **7 ngày** kể từ thời điểm giao dịch thanh toán được tạo (`app.payment.refund-max-days`).
- **Chống trùng lặp yêu cầu**: Một đơn hàng chỉ được có tối đa một phiếu hoàn tiền thông thường đang ở trạng thái Chờ xử lý (`PENDING`) hoặc Đã hoàn (`COMPLETED`) tại cùng thời điểm. Nếu đơn đang có phiếu chờ duyệt, khách cần chờ Admin xử lý xong thay vì tạo phiếu mới.
- **Đơn 0 đ (Voucher / Điểm thưởng)**: Đơn được giảm toàn bộ về `0 đ` bằng mã khuyến mãi hoặc điểm thưởng không có giao dịch thu tiền thật nên không phát sinh hoàn tiền mặt.

## Các trường hợp đủ điều kiện hoàn tiền

### 1. Hoàn tiền tự động khi huỷ đơn chưa sử dụng hoặc lỗi vận hành

Hệ thống tự động tạo yêu cầu hoàn tiền trạng thái chờ xử lý (`REFUND_PENDING`) mà khách không cần tự điền biểu mẫu lại trong các tình huống sau:

- **Đơn Gửi hàng (`SEND`) & Thuê tủ (`RENTAL`)**:
  - Khách chủ động huỷ đơn sau khi đã thanh toán nhưng **chưa bỏ hàng vào ô** (đơn còn ở trạng thái `INITIALIZED`).
  - Đơn đã thanh toán nhưng quá hạn chưa xác nhận bỏ hàng vào ô nên bộ quét tự động của hệ thống huỷ đơn.
  - Khách báo ô hỏng ngay khi đơn mới ở trạng thái `INITIALIZED` (chưa cất đồ), hệ thống tự huỷ đơn để hoàn tiền.
  - Khách chuyển khoản VietQR thành công nhưng tiền về trễ **sau khi đơn đã bị huỷ**: hệ thống ghi nhận giao dịch trễ và tự lập phiếu hoàn tiền.
- **Đơn Giao hàng Drone (`DRONE_DELIVERY`)**:
  - Khách huỷ đơn hợp lệ trước khi kỹ thuật viên lấy hàng tại điểm gửi (`INITIALIZED`, `AWAITING_DISPATCH` / chưa `PICKED_UP`).
  - Kỹ thuật viên xác nhận không thể lấy hàng từ người gửi hoặc báo huỷ do lỗi kỹ thuật drone / thời tiết không an toàn.
  - Quá thời gian chờ điều phối đội bay (mặc định `120 phút`) hoặc quá thời gian trạm tạm dừng do thời tiết (mặc định `30 phút`) mà chưa cất cánh.
  - Kiện hàng phát sinh chênh lệch kích thước/cân nặng thực tế tại điểm lấy khiến có phụ phí bổ sung, nhưng khách hàng **từ chối phụ phí** hoặc quá thời hạn thanh toán phụ phí (mặc định `60 phút`): đơn huỷ và hoàn lại phần tiền đã thanh toán ban đầu.
  - Chuyến bay gặp sự cố thất bại không thể tiếp tục giao.

### 2. Sự cố phần cứng ô tủ làm gián đoạn sử dụng

- **Mức hoàn**: Lên tới **100% cước phí đơn** (hoặc phần cước không sử dụng được).
- **Điều kiện**:
  - Khách đã thanh toán nhưng không thể mở ô cất hoặc lấy đồ do kẹt cửa, hỏng chốt khoá, lỗi kết nối IoT hoặc cúp điện kéo dài (trên 30 phút).
  - Không có ô trống khác cùng kích cỡ để điều chuyển hoặc kỹ thuật viên xác nhận phiếu sự cố thiết bị tương ứng.
  - Khi khách bấm báo ô hỏng lúc hàng đang ở trong ô (`STORING` / `RETURNED`), hệ thống tự động đóng băng đồng hồ phí quá giờ ngay tại mốc báo sự cố để khách không bị tính oan tiền quá hạn trong thời gian chờ sửa.

### 3. Thanh toán trùng 2 lần (Double Billing) hoặc lỗi cổng SePay

- **Mức hoàn**: **100% khoản tiền bị trừ thừa / trừ trùng**.
- **Điều kiện**: Khách bị trừ tiền hai lần cho cùng một mã đơn hàng hoặc cùng một lần gia hạn, hoặc tiền đã trừ khỏi tài khoản ngân hàng nhưng cổng thanh toán phản hồi chậm khiến phát sinh giao dịch trùng.
- **Khuyến nghị**: Cung cấp mã giao dịch ngân hàng hoặc ảnh chụp biên lai giao dịch bị trừ thừa khi tạo yêu cầu hoàn tiền.

### 4. Phí quá hạn tính sai

- **Mức hoàn**: Phần tiền phí quá hạn chênh lệch bị tính sai.
- **Điều kiện**: Do độ trễ cập nhật trạng thái thiết bị hoặc sự cố mở ô mà đơn bị cộng thêm phí quá giờ ngoài khoảng thời gian hợp lệ (ví dụ: hệ thống chưa kịp trừ khoảng thời gian tủ mất kết nối hoặc thời gian từ lúc khách báo ô hỏng).

### 5. Sự cố rơi hoặc hư hỏng kiện hàng Drone (Hoàn phí + Bồi thường)

- Khác với hoàn tiền cước đơn thông thường, nếu drone gặp sự cố rơi kiện hoặc hư hỏng hàng (`DroneParcelIncident`), hệ thống tạo **quy trình giải quyết sự cố riêng** sau khi KTV thu hồi và xác minh tình trạng kiện hàng.
- Admin gửi **Phương án giải quyết** cho khách hàng (người gửi) lựa chọn: **Đồng ý** hoặc **Yêu cầu xem xét lại**.
- Các phương án có thể bao gồm: giao lại miễn phí, giao lại kèm bồi thường một phần, chỉ bồi thường tiền hàng, hoặc thoả thuận riêng — và có thể kèm **hoàn 100% cước phí giao drone**.
- Sau khi khách **Đồng ý** phương án (và qua bước Admin duyệt chi nếu hệ thống bật duyệt bồi thường), hệ thống tạo phiếu hoàn/bồi thường mã riêng (`COMP-...`) để chuyển khoản về ngân hàng người gửi.

## Các trường hợp KHÔNG được hoàn tiền

- Đơn hàng có thực thu `0 đ` (đã thanh toán hoàn toàn bằng mã giảm giá hoặc điểm thưởng).
- Đơn Gửi hàng hoặc Thuê tủ đã cất đồ, sử dụng ô đúng thời hạn hoặc đã lấy hàng hoàn tất bình thường mà không có lỗi thiết bị hay sai sót thanh toán được xác nhận.
- Yêu cầu hoàn tiền gửi sau **7 ngày** kể từ thời điểm thanh toán thành công (trừ trường hợp lỗi hệ thống/bồi thường đặc biệt do Admin xử lý trực tiếp).
- Đơn đã có một yêu cầu hoàn tiền đang chờ xử lý (`PENDING`) hoặc đã được hoàn đủ toàn bộ số tiền thực thu (`COMPLETED`).

## Quy trình khách hàng gửi và nhận hoàn tiền từng bước

### Bước 1: Cập nhật tài khoản ngân hàng nhận tiền

- Vào **Tài khoản / Cài đặt thanh toán -> Tài khoản ngân hàng hoàn tiền** trong ứng dụng Lock.R.
- Nhập chính xác **Tên ngân hàng**, **Mã ngân hàng**, **Số tài khoản** và **Tên chủ tài khoản**.
- Nên lưu sẵn thông tin này làm mặc định để các đơn được hoàn tiền tự động không bị chậm do thiếu số tài khoản. Trường hợp hệ thống đã tự tạo phiếu hoàn khi bạn chưa lưu tài khoản, ứng dụng sẽ gửi thông báo nhắc bạn vào cập nhật ngay.

### Bước 2: Gửi yêu cầu hoàn tiền (nếu chưa được hoàn tự động)

1. Mở ứng dụng Lock.R -> vào **Đơn hàng** -> chọn đơn cần hoàn tiền.
2. Bấm **Yêu cầu hoàn tiền**.
3. Chọn **Lý do hoàn tiền** phù hợp trong danh sách lý do hợp lệ (ví dụ: *Tủ lỗi không mở được*, *Đơn bị huỷ sau khi đã thanh toán*, *Thanh toán trùng đơn*, *Phí quá hạn tính sai*, *Giao drone không thành công*).
4. Kiểm tra số tiền đề nghị hoàn, xác nhận tài khoản ngân hàng nhận tiền (có thể chọn lưu làm tài khoản mặc định) và **tải lên ảnh minh chứng** (ảnh màn hình trừ tiền 2 lần, ảnh chụp ô tủ kẹt...) nếu có.
5. Gửi yêu cầu; đơn chuyển sang trạng thái thanh toán `REFUND_PENDING` (Chờ hoàn tiền).

### Bước 3: Đối soát, duyệt và chuyển khoản

1. Quản trị viên tiếp nhận phiếu tại cổng quản trị `/admin/refunds`, kiểm tra nhật ký mở ô, phiếu sự cố kỹ thuật hoặc sao kê đối soát SePay.
2. Nếu hợp lệ, quản trị viên quét mã VietQR trên thông tin tài khoản bạn đã cung cấp để **chuyển khoản trực tiếp** đúng số tiền được duyệt, đính kèm ảnh chụp minh chứng chuyển khoản thành công và mã tham chiếu ngân hàng.
3. Phiếu chuyển sang trạng thái **Đã hoàn tiền (`COMPLETED`)**, trạng thái thanh toán đơn cập nhật thành **Đã hoàn (`REFUNDED`)** (hoặc giữ `PAID` nếu chỉ hoàn một phần tiền bị tính thừa).
4. Bạn nhận được thông báo trong ứng dụng kèm **ảnh minh chứng giao dịch chuyển khoản** để đối chiếu.

### Bước 4: Trường hợp yêu cầu bị từ chối

- Nếu điều kiện không khớp (ví dụ: tủ mở bình thường theo nhật ký IoT, giao dịch không bị trừ trùng, đã sử dụng xong dịch vụ), Admin sẽ từ chối phiếu (`REJECTED`) kèm **lý do từ chối cụ thể**.
- Bạn có thể xem lý do trong mục **Yêu cầu hoàn tiền** và liên hệ bộ phận hỗ trợ nếu cần bổ sung chứng từ đối soát lại.

## Ảnh hưởng tới điểm thưởng và voucher khi hoàn tiền

- **Điểm thưởng đã tích từ đơn**: Nếu đơn hoàn tất từng được cộng điểm thành viên, khi Admin hoàn tiền (toàn phần hoặc một phần), hệ thống tự động gửi sự kiện để **thu hồi lại số điểm tương ứng** theo tỉ lệ `(Số tiền hoàn / Số tiền đã thanh toán)`, không trừ âm dưới `0`.
- **Điểm thưởng đã dùng để giảm giá khi đặt đơn**: Nếu đơn bị huỷ trước khi hoàn tất sử dụng, số điểm khách đã trừ cho đơn đó được hoàn trả lại vào tài khoản thành viên.
