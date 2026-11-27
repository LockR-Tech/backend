# Quy trình duyệt hoàn tiền, bồi thường và đối soát cho Quản trị viên

Tài liệu hướng dẫn nghiệp vụ nội bộ dành riêng cho **Quản trị viên (ADMIN)** khi xử lý yêu cầu hoàn tiền tại `/admin/refunds`, duyệt bồi thường sự cố Drone, trao trả đồ lưu kho quá hạn tại `/admin/storage` và quản lý cấu hình hệ thống.

## Quy trình 4 bước xét duyệt hoàn tiền trên `/admin/refunds`

1. **Bước 1 — Kiểm tra thông tin STK**: Đối chiếu **Tên chủ tài khoản**, **Số tài khoản** và **Mã ngân hàng** khách đã cung cấp hoặc lưu trong hệ thống, xác nhận đúng chủ đơn hàng.
2. **Bước 2 — Quét mã VietQR chuyển tiền**: Trên modal xét duyệt, quét mã VietQR được sinh tự động bằng app Mobile Banking (điền sẵn STK, số tiền duyệt hoàn và nội dung đối soát) và thực hiện chuyển khoản thật.
3. **Bước 3 — Tải ảnh chụp minh chứng**: Chụp lại màn hình biên lai chuyển khoản thành công, tải lên ô ảnh minh chứng (`proofImageUrl`) và nhập **Mã tham chiếu giao dịch ngân hàng (`bankTransferRef`)** (bắt buộc nếu cấu hình `app.payment.refund-require-transfer-ref` đang bật).
4. **Bước 4 — Xác nhận hoàn tiền (hoặc Từ chối)**:
   - Bấm **Duyệt & Xác nhận hoàn tiền**: phiếu chuyển sang `COMPLETED`.
   - Nếu yêu cầu không đủ điều kiện: bấm **Từ chối**, nhập rõ **lý do từ chối** gửi tới khách hàng (`REJECTED`); hệ thống tự khôi phục trạng thái thanh toán trước đó của đơn (`PAID` nếu đơn đã trả tiền).

## Ma trận 4 tình huống đối soát hoàn tiền hiện hành

- **1. Sự cố ô tủ Lock.R (Kẹt cửa, hỏng chốt, mất nguồn/mạng)**:
  - *Mức hoàn*: Lên tới **100% cước phí đơn**.
  - *Cần đối chiếu*: Phiếu sự cố `RPT-...` của KTV + nhật ký lệnh mở tủ thất bại trên IoT; xác nhận khách chưa thể cất/lấy đồ hoặc bị gián đoạn toàn bộ giao dịch (không có ô trống cùng cỡ thay thế).
- **2. Sự cố giao hàng Drone (Huỷ bay, rơi đồ, giao trễ, quá hạn chờ)**:
  - *Mức hoàn*: **100% cước phí** (đơn huỷ/lỗi bay) hoặc theo phương án **Hoàn phí + Bồi thường** nếu là sự cố rơi/hỏng kiện (`COMP-...`).
  - *Cần đối chiếu*: Mã chuyến bay Drone, trạng thái đơn (`CANCELED` trước lấy hàng, timeout điều phối 120 phút / trạm dừng 30 phút, từ chối phụ phí 60 phút), hoặc biên bản thu hồi + phương án đã được khách bấm `ACCEPT`.
- **3. Khách hàng huỷ đơn hợp lệ trước khi sử dụng**:
  - *Mức hoàn*: **100% khoản thực thu**.
  - *Cần đối chiếu*: Lịch sử trạng thái xác nhận ô tủ chưa từng mở để bỏ đồ (`dropOpenedAt == null`), mã PIN chưa sử dụng, và thời điểm yêu cầu nằm trong giới hạn `refund-max-days` (mặc định `7 ngày`).
- **4. Lỗi cổng thanh toán / Trừ tiền 2 lần (Double Billing)**:
  - *Mức hoàn*: **100% số tiền bị trừ trùng / trừ thừa**.
  - *Cần đối chiếu*: Sao kê ngân hàng, `referenceId` và `referenceTransactionId` trong bảng `PaymentRecord` hiển thị nhiều giao dịch `COMPLETED` cho cùng một `orderId`. Chỉ hoàn khoản trừ thừa, giữ nguyên khoản thanh toán chính thức của đơn (`order.paymentStatus` giữ `PAID`).

## Hoàn tiền từng phần, thanh toán đến muộn và thu hồi điểm thưởng

- **Hoàn tiền toàn phần vs một phần**:
  - Khi tổng số tiền các phiếu hoàn `COMPLETED` trên một đơn bằng toàn bộ số tiền thực thu, trạng thái thanh toán của đơn chuyển thành **`REFUNDED`**.
  - Nếu Admin chỉ hoàn một phần (ví dụ hoàn tiền trừ trùng của 1 giao dịch thừa, hoặc hoàn phần phí quá giờ tính sai trên đơn đã hoàn tất), hệ thống giữ trạng thái đơn là **`PAID`** để phản ánh đơn vẫn đã thanh toán cho phần dịch vụ hợp lệ.
- **Thanh toán đến muộn sau khi đơn đã huỷ (`DroneLateRefundScheduler`)**:
  - Nếu SePay báo tiền vào sau khi đơn đã bị `CANCELED` (quá hạn bỏ hàng hoặc khách huỷ), listener/scheduler tự đánh dấu hoàn tiền chờ xử lý (`REFUND_PENDING`) để Admin kiểm tra chuyển trả lại cho khách.
- **Tự động thu hồi điểm thưởng (`ORDER_EARN_REVERSAL`)**:
  - Khi Admin duyệt hoàn tiền thành công, `payment-service` phát sự kiện `payment.refunded`. `loyalty-service` tự tính số điểm cần thu hồi theo công thức `điểm đã tích × (tổng tiền đã hoàn / tổng tiền đã thu)` (làm tròn), trừ vào số dư điểm hiện có của khách (tối đa bằng số dư còn lại, không âm).
- **Doanh thu thực thu (`/admin/revenue`)**: Báo cáo doanh thu tách riêng doanh thu gộp, tổng tiền hoàn đã xử lý xong trong kỳ và **Thực thu sau hoàn (`Net After Refund`)**.

## Duyệt bồi thường sự cố rơi kiện Drone (`DroneParcelIncident`)

1. Sau khi KTV Drone thu hồi kiện và bàn giao Hub, Admin thực hiện **Xác minh thu hồi (`verifyRecovery`)**.
2. Admin lập **Phương án giải quyết (`propose`)** dựa trên mức gợi ý hệ thống và chính sách đơn (`FREE_REDELIVERY`, `REDELIVERY_PARTIAL_COMPENSATION`, `COMPENSATION_ONLY`, hoặc `MANUAL_RESOLUTION`), kèm lựa chọn hoàn phí giao hàng hay không. Nếu vượt mức gợi ý, bắt buộc nhập lý do điều chỉnh (`overrideReason`).
3. Khách hàng (chỉ **người gửi đơn**) xem phương án trên app và bấm **Đồng ý (`ACCEPT`)** hoặc **Yêu cầu xem xét lại (`REQUEST_REVIEW`)**. Nếu khách yêu cầu xem xét lại, Admin chỉnh và gửi phiên bản phương án mới.
4. Khi khách bấm `ACCEPT`: nếu cấu hình yêu cầu Admin duyệt chi (`approvalRequired = true`), khoản bồi thường nằm ở `AWAITING_APPROVAL`; Admin bấm **Duyệt chi bồi thường (`approveCompensation`)** để `payment-service` tạo phiếu bồi thường (`COMP-...`) xuất hiện trên `/admin/refunds` cho Admin chuyển khoản và xác nhận.

## Trao trả đồ lưu kho quá hạn tại quầy (`/admin/storage`)

- Đơn gửi/thuê quá thời gian giữ hàng tối đa tại tủ được chuyển sang trạng thái **`EXPIRED`** và giải phóng ô về `AVAILABLE`; tài sản được quản lý tại trang **Đồ lưu kho (`/admin/storage`)**.
- Quy trình bàn giao tại quầy bắt buộc thực hiện qua trang **Đồ lưu kho** (không dùng thao tác đổi trạng thái thủ công):
  1. Xác định loại người tới nhận (`SENDER`, `RECEIVER`, `DELEGATE`, hoặc `OTHER`). Nếu thuộc đơn, đối chiếu **4 số cuối SĐT** khớp với người nhận tương ứng; nếu là người nhận hộ ngoài đơn (`OTHER`), ghi đủ họ tên, SĐT và lý do bàn giao.
  2. Nếu đơn còn công nợ chưa thanh toán (phí quá giờ / phí lưu), thu đủ qua **Tiền mặt (`CASH`)** hoặc **Chuyển khoản (`TRANSFER`)** và xác nhận đã thu.
  3. Chụp **ảnh biên bản trao trả thực tế (`STORAGE_HANDOVER`)** và ghi chú để hệ thống lưu vết bàn giao và chốt đơn sang **`COMPLETED`**.

## Các tham số cấu hình liên quan tại `/admin/settings`

- **Nhóm Hoàn tiền (`PaymentSettingsCatalog`)**: `app.payment.refund-enabled` (Bật/tắt hoàn tiền, mặc định `true`), `refund-min-amount` (`1.000 đ`), `refund-max-days` (`7 ngày`), `refund-allowed-reasons`, `refund-require-transfer-ref` (`false`).
- **Nhóm Thuê tủ & Gửi hàng (`OrderSettingsCatalog`)**: Đơn giá giờ & trần ngày cho ô `STANDARD` / `XL`, tỉ lệ phí quá giờ thuê, thời gian ân hạn quá giờ, trần % & trần tiền quá giờ gửi hàng, thời hạn tự huỷ chưa bỏ hàng, thời hạn đưa vào kho.
- **Nhóm Bảo trì & KTV (`LockerSettingsCatalog`)**: Hạn SLA xử lý phiếu sự cố, ngưỡng cảnh báo/hạn chế/đình chỉ KTV theo số phiếu trễ SLA, bắt buộc ảnh nghiệm thu, giới hạn số lượng/dung lượng ảnh.
