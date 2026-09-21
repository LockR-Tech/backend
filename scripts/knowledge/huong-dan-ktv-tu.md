# Quy trình vận hành và bảo trì tủ cho kỹ thuật viên

Tài liệu dành cho kỹ thuật viên tủ (KTV tủ) và admin. KTV tủ phụ trách ô tủ, bãi đáp drone trên nóc tủ và kiểm tra định kỳ tủ. Drone vật lý là việc của KTV drone.

## Phân công tủ

- Admin gán một KTV tủ phụ trách cho từng tủ trên web admin (trang chi tiết tủ, mục KTV phụ trách).
- Phiếu sự cố mới của tủ được báo cho KTV phụ trách tủ đó. Tủ chưa có KTV phụ trách thì phiếu được báo cho mọi KTV tủ.
- Khi admin đổi KTV phụ trách, các phiếu đang chờ nhận của tủ chuyển sang người mới và người mới nhận thông báo.
- Trên ứng dụng KTV, mục "Tủ tôi phụ trách" liệt kê các phiếu đang chờ bạn nhận.

## Nhận phiếu sự cố

- Phiếu do khách báo ở trạng thái Chờ tiếp nhận (OPEN). KTV bấm Nhận việc để phiếu chuyển sang Đang xử lý và tính SLA cho mình. Phiếu được báo cho KTV phụ trách nhưng KTV khác vẫn nhận được khi cần hỗ trợ.
- Phiếu do chính KTV tủ tạo (báo ô hỏng, báo sự cố tủ, bãi đáp) được giao luôn cho người tạo.
- Admin có thể giao phiếu trực tiếp cho một KTV; KTV được giao nhận thông báo.
- Không nhận được phiếu mới khi đang giữ quá nhiều phiếu trễ hạn SLA: hãy xử lý dứt điểm các phiếu trễ trước. Ngưỡng do admin cấu hình.
- Nếu một ô đã có phiếu đang mở, các lần báo hỏng tiếp theo cho ô đó được gộp vào phiếu cũ (thêm nhật ký và ảnh), không tạo phiếu mới.

## Xử lý và hoàn tất phiếu

1. Trong lúc sửa, ghi nhật ký xử lý và chụp ảnh quá trình trên phiếu.
2. Khi sửa xong, bấm Hoàn tất trên phiếu, ghi chú nghiệm thu và chụp ảnh nghiệm thu. Nếu admin bật "Bắt buộc ảnh nghiệm thu", phải có ít nhất một ảnh nghiệm thu mới hoàn tất được.
3. Chỉ KTV đang được giao phiếu (hoặc admin) mới hoàn tất được phiếu. Phiếu đã hoàn tất không hoàn tất lại được.
4. Hoàn tất phiếu sẽ tự đưa tài sản về hoạt động:
   - Ô về lại trạng thái trước khi hỏng: ô đang chứa hàng trở về "có hàng", ô trống trở về "trống".
   - Bãi đáp drone về trạng thái OK.
   - Tủ bị ngưng do phiếu sự cố cả tủ trở lại hoạt động khi phiếu chặn cuối cùng được hoàn tất. Tủ do admin tự bật bảo trì thì vẫn giữ bảo trì.
5. Khách báo phiếu nhận thông báo khi phiếu được tiếp nhận và khi hoàn tất, và có thể đánh giá kết quả xử lý.

## Nút "Đã sửa" trên ô hỏng

- Nếu ô hỏng đang có phiếu mở, "Đã sửa" mở màn hình hoàn tất phiếu đó (ghi chú + ảnh nghiệm thu). Nếu phiếu chưa giao cho bạn, hệ thống báo "Đang có phiếu sự cố — nhận phiếu rồi hoàn tất phiếu để khôi phục".
- Nếu ô hỏng không có phiếu nào, "Đã sửa" đưa ô về trạng thái trước khi hỏng ngay.
- "Đã sửa" chỉ dùng cho ô đang ở trạng thái Hỏng.

## Báo sự cố cả tủ

- KTV tủ và admin có thể báo sự cố cả tủ ở chế độ ngưng tủ: tủ chuyển sang Bảo trì và không nhận đơn hay đặt ô mới cho tới khi phiếu được hoàn tất. Khách hàng không dùng được chế độ ngưng tủ.
- Tủ ở trạng thái Bảo trì hoặc Vô hiệu không nhận đặt ô, kể cả ô nhận hàng drone.

## Bãi đáp drone

- Với tủ có bãi đáp, KTV cập nhật trạng thái bãi đáp: OK, Hỏng (FAULT) hoặc Bảo trì (MAINTENANCE), kèm lý do.
- Chuyển sang Hỏng hoặc Bảo trì sẽ mở một phiếu bãi đáp (mỗi bãi đáp chỉ một phiếu mở), giao cho KTV đã báo.
- Chuyển lại OK khi đang có phiếu bãi đáp nghĩa là hoàn tất phiếu đó, áp dụng cùng quy tắc với hoàn tất phiếu thường.

## Ô tạm ngưng và vệ sinh

- Ô trống có thể đưa vào Tạm ngưng dùng (kèm lý do) hoặc Đang vệ sinh; ô đang có đơn giữ thì không được.
- Khôi phục ô tạm ngưng/vệ sinh bằng nút Khôi phục. Ô hỏng thì khôi phục qua phiếu như trên.

## Mở ô khẩn cấp

Mở khẩn cấp mở ô không cần mã của khách. Mọi lần mở khẩn cấp đều được ghi vào nhật ký kiểm toán kèm người thực hiện; chỉ dùng khi hỗ trợ khách hoặc xử lý sự cố.

## Kiểm tra định kỳ

- Admin tạo lịch kiểm tra định kỳ cho từng tủ: chu kỳ (số ngày), KTV phụ trách lịch, khung giờ, vị trí và checklist (mỗi dòng một mục).
- Mục Định kỳ trong ứng dụng có bộ lọc "Của tôi" để xem các lịch mình phụ trách. Chỉ KTV phụ trách lịch (hoặc admin) được ghi kết quả kiểm tra.
- Khi kiểm tra, đánh giá từng mục: Đạt, Không đạt hoặc Không áp dụng, kèm ghi chú nếu cần. Phải đánh giá đủ mọi mục.
- Tất cả mục Đạt hoặc Không áp dụng: lần kiểm tra ĐẠT, hạn kiểm tra kế tiếp được dời thêm một chu kỳ.
- Có mục Không đạt: lần kiểm tra KHÔNG ĐẠT. Hệ thống tự mở phiếu sự cố gắn với lịch và giao cho KTV vừa kiểm tra; nếu chọn ô hỏng thì ô đó chuyển sang Hỏng. Hạn kiểm tra không được dời cho tới khi phiếu này được hoàn tất; hoàn tất phiếu thì hạn kế tiếp tự dời một chu kỳ.
- Trong lúc lịch đang chờ phiếu của lần kiểm tra không đạt, không ghi được lần kiểm tra mới.
- Mỗi sáng lúc 07:00 hệ thống nhắc KTV các lịch sắp tới hạn hoặc đã tới hạn; mỗi kỳ hạn chỉ nhắc một lần. Lịch chưa có KTV phụ trách thì nhắc mọi KTV của mảng đó.

## SLA và chế tài

- Mỗi phiếu có hạn xử lý (SLA) tính từ lúc tạo phiếu; số giờ SLA do admin cấu hình. Phiếu quá hạn bị đánh dấu trễ hạn.
- Có thể xin gia hạn SLA kèm lý do; mỗi lần gia hạn được ghi vào nhật ký phiếu.
- Số phiếu trễ hạn đang giữ quyết định mức chế tài: Cảnh báo, Hạn chế nhận việc, Đình chỉ. Ngưỡng từng mức do admin cấu hình. Xem hiệu suất và đánh giá của mình trong hồ sơ KTV.

## Thông báo KTV nhận được

- Phiếu sự cố mới cần xử lý (tủ mình phụ trách, hoặc mọi KTV tủ khi tủ chưa có người phụ trách).
- Được giao việc bảo trì: admin giao phiếu, hoặc giao phụ trách một tủ.
- Lịch kiểm tra định kỳ tới hạn.
