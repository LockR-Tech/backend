package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.client.LockerCellClient;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.dto.GuestNotification;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Thư thật (SMTP của notification-service) cho người gửi và người nhận của một đơn drone:
 *
 * <ul>
 *   <li>khi drone được phóng — người gửi biết đơn đang đi tới ai, người nhận biết có đơn từ ai;</li>
 *   <li>khi đơn kết thúc "giao không hoàn tất" — lý do, hoàn tiền và kiện được trả thế nào.</li>
 * </ul>
 *
 * Email người gửi và người nhận là bắt buộc lúc đặt đơn. Không bao giờ ném lỗi: hỏng kênh thư không
 * được làm hỏng chuyến bay hay việc hoàn tiền; nơi gọi ghi vào nhật ký hành trình thư đã tới ai.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DroneDeliveryMailer {

    private final NotificationClient notificationClient;
    private final LockerLookupClient lockerLookupClient;
    private final LockerCellClient lockerCellClient;

    /// Kết quả gửi: địa chỉ (đã che) của những người thực sự nhận được thư.
    public record Outcome(boolean senderReached, boolean receiverReached) {

        public String describe(LockerOrder order) {
            List<String> reached = new ArrayList<>();
            if (senderReached) {
                reached.add("người gửi " + mask(order.getSenderEmail()));
            }
            if (receiverReached) {
                reached.add("người nhận " + mask(order.getReceiverEmail()));
            }
            return reached.isEmpty() ? null : String.join(", ", reached);
        }
    }

    /// Drone vừa cất cánh: báo cho cả hai đầu.
    public Outcome launched(LockerOrder order, DroneMission mission) {
        String destination = lockerLabel(order.getDestinationLockerId());
        String box = boxLabel(order);
        String parcel = parcelLine(order);
        String drone = mission != null && StringUtils.hasText(mission.getDroneCode())
                ? " bằng drone " + mission.getDroneCode()
                : " bằng drone";

        String senderBody = "Xin chào " + nameOr(order.getSenderName(), "bạn") + ",\n\n"
                + "Đơn hàng " + order.getOrderCode() + " của bạn đã được phóng đi" + drone + ".\n\n"
                + "Gửi tới:\n"
                + "  Người nhận: " + nameOr(order.getReceiverName(), "—") + "\n"
                + "  Số điện thoại: " + nameOr(order.getReceiverPhone(), "—") + "\n"
                + "  Email: " + nameOr(order.getReceiverEmail(), "—") + "\n"
                + "  Tủ nhận: " + destination + "\n"
                + "  Ô nhận: " + box + "\n\n"
                + "Kiện hàng: " + parcel + "\n\n"
                + "Khi hàng vào tủ, người nhận sẽ được gửi mã mở ô. Bạn theo dõi hành trình trong ứng dụng Lock.R.\n";
        String senderHtml = DroneDeliveryEmailTemplate.launched(
                nameOr(order.getSenderName(), "bạn"),
                order.getOrderCode(),
                "Đơn hàng đang trên đường",
                "Đơn hàng của bạn đã được phóng đi" + drone + " và đang được chuyển tới người nhận.",
                List.of(
                        new DroneDeliveryEmailTemplate.Detail("Người nhận", nameOr(order.getReceiverName(), "—")),
                        new DroneDeliveryEmailTemplate.Detail("Điện thoại", nameOr(order.getReceiverPhone(), "—")),
                        new DroneDeliveryEmailTemplate.Detail("Email", nameOr(order.getReceiverEmail(), "—")),
                        new DroneDeliveryEmailTemplate.Detail("Tủ nhận", destination),
                        new DroneDeliveryEmailTemplate.Detail("Ô nhận", box),
                        new DroneDeliveryEmailTemplate.Detail("Kiện hàng", parcel)),
                "Khi kiện hàng vào tủ, người nhận sẽ nhận hướng dẫn và mã mở ô qua email.");

        String receiverBody = "Xin chào " + nameOr(order.getReceiverName(), "bạn") + ",\n\n"
                + "Bạn có một đơn hàng " + order.getOrderCode() + " đang được giao tới bạn" + drone + ".\n\n"
                + "Gửi từ:\n"
                + "  Người gửi: " + nameOr(order.getSenderName(), "—") + "\n"
                + "  Số điện thoại: " + nameOr(order.getSenderPhone(), "—") + "\n"
                + "  Email: " + nameOr(order.getSenderEmail(), "—") + "\n\n"
                + "Kiện hàng: " + parcel + "\n"
                + "Nơi nhận: " + destination + "\n"
                + "Ô nhận: " + box + "\n\n"
                + "Khi hàng vào tủ, bạn sẽ nhận được email kèm mã mở ô và hạn lấy hàng.\n";
        String receiverHtml = DroneDeliveryEmailTemplate.launched(
                nameOr(order.getReceiverName(), "bạn"),
                order.getOrderCode(),
                "Bạn có một đơn hàng đang được gửi tới",
                "Đơn hàng đang được chuyển tới bạn" + drone + ".",
                List.of(
                        new DroneDeliveryEmailTemplate.Detail("Người gửi", nameOr(order.getSenderName(), "—")),
                        new DroneDeliveryEmailTemplate.Detail("Điện thoại", nameOr(order.getSenderPhone(), "—")),
                        new DroneDeliveryEmailTemplate.Detail("Email", nameOr(order.getSenderEmail(), "—")),
                        new DroneDeliveryEmailTemplate.Detail("Kiện hàng", parcel),
                        new DroneDeliveryEmailTemplate.Detail("Nơi nhận", destination),
                        new DroneDeliveryEmailTemplate.Detail("Ô nhận", box)),
                "Khi hàng vào tủ, bạn sẽ nhận email có mã mở ô và thời hạn lấy hàng.");

        return new Outcome(
                send(order.getSenderEmail(), "Đơn hàng " + order.getOrderCode() + " đã được drone phóng đi", senderBody,
                        senderHtml),
                send(order.getReceiverEmail(), "Bạn có đơn hàng " + order.getOrderCode() + " đang được giao bằng drone",
                        receiverBody, receiverHtml));
    }

    /**
     * Đơn kết thúc "giao không hoàn tất".
     *
     * @param refundExpected  đơn đã thu tiền nên sẽ có yêu cầu hoàn tiền
     * @param parcelCollected kiện đã rời tay người gửi nên đội bay phải mang trả
     * @param tellReceiver    người nhận đã được báo có đơn (drone đã phóng) nên cũng phải được báo huỷ
     */
    public Outcome failed(LockerOrder order, String reasonLabel, String note, Long ticketId,
            boolean refundExpected, boolean parcelCollected, boolean tellReceiver) {
        String why = reasonLabel
                + (StringUtils.hasText(note) ? " — " + note.trim() : "")
                + (ticketId == null ? "" : " (phiếu sự cố #" + ticketId + ")");
        String senderBody = "Xin chào " + nameOr(order.getSenderName(), "bạn") + ",\n\n"
                + "Rất tiếc, đơn hàng " + order.getOrderCode() + " giao bằng drone không hoàn tất.\n\n"
                + "Lý do: " + why + "\n\n"
                + (refundExpected
                        ? "Hoàn tiền: yêu cầu hoàn số tiền bạn đã thanh toán đã được ghi nhận; quản trị viên sẽ "
                                + "chuyển khoản về tài khoản ngân hàng của bạn.\n"
                        : "Đơn chưa được thanh toán nên không phát sinh hoàn tiền.\n")
                + (parcelCollected
                        ? "Kiện hàng: kỹ thuật viên sẽ liên hệ và mang kiện trả lại cho bạn tại "
                                + nameOr(order.getPickupAddress(), "điểm lấy hàng") + ".\n"
                        : "Kiện hàng: chưa được lấy, vẫn ở chỗ bạn.\n")
                + "\nBạn xem chi tiết và trạng thái hoàn tiền trong ứng dụng Lock.R.\n";
        String refundMessage = refundExpected
                ? "Yêu cầu hoàn tiền đã được ghi nhận; quản trị viên sẽ chuyển khoản về tài khoản ngân hàng của bạn."
                : "Đơn chưa thanh toán nên không phát sinh hoàn tiền.";
        String returnMessage = parcelCollected
                ? "Kỹ thuật viên sẽ liên hệ và mang kiện trả tại "
                        + nameOr(order.getPickupAddress(), "điểm lấy hàng") + "."
                : "Kiện chưa được lấy và vẫn ở chỗ bạn.";
        String senderHtml = DroneDeliveryEmailTemplate.failed(
                nameOr(order.getSenderName(), "bạn"),
                order.getOrderCode(),
                "Rất tiếc, đơn hàng drone không thể giao hoàn tất.",
                List.of(
                        new DroneDeliveryEmailTemplate.Detail("Lý do", why),
                        new DroneDeliveryEmailTemplate.Detail("Hoàn tiền", refundMessage),
                        new DroneDeliveryEmailTemplate.Detail("Kiện hàng", returnMessage)),
                "Bạn xem chi tiết đơn và trạng thái hoàn tiền trong ứng dụng Lock.R.");
        boolean sender = send(order.getSenderEmail(),
                "Đơn hàng " + order.getOrderCode() + " giao không hoàn tất", senderBody, senderHtml);

        boolean receiver = false;
        if (tellReceiver) {
            String receiverBody = "Xin chào " + nameOr(order.getReceiverName(), "bạn") + ",\n\n"
                    + "Đơn hàng " + order.getOrderCode() + " do " + nameOr(order.getSenderName(), "người gửi")
                    + " gửi cho bạn bằng drone không hoàn tất.\n\n"
                    + "Lý do: " + why + "\n\n"
                    + "Kiện hàng sẽ được trả lại người gửi. Vui lòng liên hệ người gửi ("
                    + nameOr(order.getSenderPhone(), "—") + ") nếu cần.\n";
            String receiverHtml = DroneDeliveryEmailTemplate.failed(
                    nameOr(order.getReceiverName(), "bạn"),
                    order.getOrderCode(),
                    "Đơn hàng do " + nameOr(order.getSenderName(), "người gửi")
                            + " gửi cho bạn không thể giao hoàn tất.",
                    List.of(
                            new DroneDeliveryEmailTemplate.Detail("Lý do", why),
                            new DroneDeliveryEmailTemplate.Detail("Kiện hàng", "Đơn vị vận hành sẽ trả kiện cho người gửi."),
                            new DroneDeliveryEmailTemplate.Detail("Liên hệ người gửi", nameOr(order.getSenderPhone(), "—"))),
                    "Bạn có thể liên hệ người gửi nếu cần hỗ trợ thêm.");
            receiver = send(order.getReceiverEmail(),
                    "Đơn hàng " + order.getOrderCode() + " gửi cho bạn không hoàn tất", receiverBody, receiverHtml);
        }
        return new Outcome(sender, receiver);
    }

    private boolean send(String email, String subject, String body, String htmlBody) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        try {
            // Không gửi SMS (phone = null), nhưng notification-service bắt buộc `smsMessage`
            // khác rỗng với mọi yêu cầu — thiếu thì cả yêu cầu bị từ chối 400 và thư không đi.
            var response = notificationClient.notifyGuest(
                    new GuestNotification.Request(null, email.trim(), subject, "Lock.R: " + subject, body, htmlBody));
            GuestNotification.Result result = response == null ? null : response.data();
            return result != null && result.emailSent();
        } catch (Exception ex) {
            log.warn("Could not email {} about a drone order ({}): {}", mask(email),
                    ex.getClass().getSimpleName(), ex.getMessage(), ex);
            return false;
        }
    }

    /// Tủ nhận kèm ô đã giữ cho đơn — người nhận cần biết tới tủ nào, mở ô số mấy.
    public String destinationWithBox(LockerOrder order) {
        return lockerLabel(order.getDestinationLockerId()) + " · " + boxLabel(order);
    }

    /// "Ô số 3": số in trên cửa ô nhận, không phải id nội bộ.
    String boxLabel(LockerOrder order) {
        Long boxId = order.getReservedBoxId();
        if (boxId == null) {
            return "chưa gán ô";
        }
        try {
            var response = lockerCellClient.getCell(boxId);
            var cell = response == null ? null : response.data();
            if (cell != null && cell.boxNumber() != null) {
                return "Ô số " + cell.boxNumber();
            }
        } catch (Exception ex) {
            log.debug("Box {} lookup failed while composing a drone email: {}", boxId, ex.getMessage());
        }
        return "ô #" + boxId;
    }

    /// Tên + địa chỉ tủ: người nhận cần biết đi đâu, "tủ #7" thì không giúp được gì.
    private String lockerLabel(Long lockerId) {
        if (lockerId == null) {
            return "tủ Lock.R";
        }
        try {
            var response = lockerLookupClient.getLockers(List.of(lockerId));
            List<LockerInfo> lockers = response == null ? null : response.data();
            if (lockers != null) {
                for (LockerInfo locker : lockers) {
                    if (Objects.equals(locker.id(), lockerId)) {
                        String name = StringUtils.hasText(locker.name()) ? locker.name() : "tủ #" + lockerId;
                        return StringUtils.hasText(locker.address()) ? name + " (" + locker.address() + ")" : name;
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("Locker {} lookup failed while composing a drone email: {}", lockerId, ex.getMessage());
        }
        return "tủ #" + lockerId;
    }

    private static String parcelLine(LockerOrder order) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(order.getDescription())) {
            parts.add(order.getDescription().trim());
        }
        if (order.getParcelWeightGrams() != null) {
            parts.add(order.getParcelWeightGrams() + " g");
        }
        if (Boolean.TRUE.equals(order.getParcelFragile())) {
            parts.add("dễ vỡ");
        }
        return parts.isEmpty() ? "—" : String.join(" · ", parts);
    }

    private static String nameOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    /// `kh***@gmail.com` — nhật ký hành trình hiện cho cả người gửi lẫn nhân viên.
    static String mask(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }
}
