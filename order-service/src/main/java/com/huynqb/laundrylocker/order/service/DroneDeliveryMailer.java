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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter INCIDENT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

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

    /**
     * Kiện rơi khỏi drone khi đang bay. Người gửi và người nhận đã nhận thư "đang trên đường" nên phải được báo
     * ngay: kiện chưa tới, đang được thu hồi, phương án xử lý sẽ gửi qua ứng dụng.
     *
     * @param reportedAt   lúc báo rơi
     * @param location     toạ độ báo rơi ("10.84110, 106.80970"), null nếu không có
     * @param flightStage  chặng bay lúc rơi ("drone đang bay"…). Không trích ghi chú nội bộ của đội bay.
     * @param tellReceiver drone đã phóng (người nhận đã được báo có đơn)
     */
    public Outcome parcelDropReported(LockerOrder order, String incidentCode, Instant reportedAt, String location,
            String flightStage, boolean tellReceiver) {
        List<DroneDeliveryEmailTemplate.Detail> incident = new ArrayList<>();
        incident.add(new DroneDeliveryEmailTemplate.Detail("Mã sự cố", incidentCode));
        incident.add(new DroneDeliveryEmailTemplate.Detail("Thời gian", displayTime(reportedAt)));
        if (StringUtils.hasText(location)) {
            incident.add(new DroneDeliveryEmailTemplate.Detail("Vị trí báo rơi", location));
        }
        incident.add(new DroneDeliveryEmailTemplate.Detail("Mô tả", "Kiện rơi khỏi drone trong lúc bay"
                + (StringUtils.hasText(flightStage) ? " (" + flightStage.trim() + ")" : "") + "."));
        incident.add(new DroneDeliveryEmailTemplate.Detail("Trạng thái", "Đang tìm và thu hồi kiện"));

        List<DroneDeliveryEmailTemplate.Detail> senderDetails = new ArrayList<>(incident);
        senderDetails.add(new DroneDeliveryEmailTemplate.Detail("Đang xử lý",
                "Kỹ thuật viên Lock.R đang tới khu vực rơi để tìm và thu hồi kiện."));
        senderDetails.add(new DroneDeliveryEmailTemplate.Detail("Tiếp theo",
                "Sau khi kiểm tra kiện, Lock.R gửi phương án xử lý (giao lại, bồi thường) trong ứng "
                        + "dụng để bạn đồng ý hoặc yêu cầu xem xét lại."));
        boolean sender = sendNotice(order.getSenderEmail(), nameOr(order.getSenderName(), "bạn"), order,
                "Đơn hàng " + order.getOrderCode() + " gặp sự cố khi đang bay",
                "Kiện hàng gặp sự cố trên đường bay",
                "Rất tiếc, kiện hàng của đơn " + order.getOrderCode() + " đã rơi khỏi drone khi đang bay tới tủ nhận.",
                senderDetails,
                "Bạn theo dõi sự cố trong ứng dụng Lock.R: mở đơn " + order.getOrderCode() + " ⇒ Theo dõi drone.");

        List<DroneDeliveryEmailTemplate.Detail> receiverDetails = new ArrayList<>(incident);
        receiverDetails.add(new DroneDeliveryEmailTemplate.Detail("Đang xử lý",
                "Lock.R đang thu hồi kiện và sẽ thống nhất cách xử lý với người gửi."));
        receiverDetails.add(new DroneDeliveryEmailTemplate.Detail("Liên hệ người gửi",
                nameOr(order.getSenderPhone(), "—")));
        boolean receiver = tellReceiver && sendNotice(order.getReceiverEmail(),
                nameOr(order.getReceiverName(), "bạn"), order,
                "Đơn hàng " + order.getOrderCode() + " gửi cho bạn bị chậm do sự cố",
                "Đơn hàng gửi cho bạn bị chậm",
                "Đơn hàng " + order.getOrderCode() + " do " + nameOr(order.getSenderName(), "người gửi")
                        + " gửi cho bạn gặp sự cố khi đang bay và chưa thể vào tủ nhận.",
                receiverDetails,
                "Mã mở ô cũ (nếu có) không còn dùng được. Lock.R sẽ báo lại khi kiện được giao.");
        return new Outcome(sender, receiver);
    }

    /**
     * Admin đã xác minh kết quả thu hồi kiện rơi. Gửi sau mail báo rơi (sự cố chỉ tới bước này sau khi đã báo rơi),
     * trước mail phương án xử lý.
     *
     * @param found         KTV tìm thấy kiện (FOUND); false ⇒ chưa tìm thấy (NOT_FOUND / không tiếp cận được)
     * @param parcelState   tình trạng kiện ("còn nguyên vẹn", "hư hại nhẹ"…), null khi chưa tìm thấy
     * @param stationName   trạm bay kiện được điều phối về để giao lại, null khi chưa điều phối
     * @param tellReceiver  người nhận đã được báo có đơn (drone đã phóng)
     */
    public Outcome parcelRecoveryVerified(LockerOrder order, String incidentCode, boolean found, String parcelState,
            String stationName, Instant verifiedAt, boolean tellReceiver) {
        List<DroneDeliveryEmailTemplate.Detail> details = new ArrayList<>();
        details.add(new DroneDeliveryEmailTemplate.Detail("Mã sự cố", incidentCode));
        details.add(new DroneDeliveryEmailTemplate.Detail("Thời gian xác nhận", displayTime(verifiedAt)));
        details.add(new DroneDeliveryEmailTemplate.Detail("Kết quả", found ? "Đã tìm thấy kiện" : "Chưa tìm thấy kiện"));
        if (found && StringUtils.hasText(parcelState)) {
            details.add(new DroneDeliveryEmailTemplate.Detail("Tình trạng kiện", parcelState));
        }
        details.add(new DroneDeliveryEmailTemplate.Detail("Tiếp theo", found
                ? "Kiện được đưa về " + (StringUtils.hasText(stationName) ? "trạm " + stationName.trim() : "trạm Lock.R")
                        + ". Lock.R sẽ gửi phương án xử lý trong ứng dụng."
                : "Lock.R sẽ gửi phương án xử lý trong ứng dụng để bạn đồng ý hoặc yêu cầu xem xét lại."));
        boolean sender = sendNotice(order.getSenderEmail(), nameOr(order.getSenderName(), "bạn"), order,
                found
                        ? "Đã tìm thấy kiện hàng của đơn " + order.getOrderCode()
                        : "Chưa tìm thấy kiện hàng của đơn " + order.getOrderCode(),
                found ? "Đã tìm thấy kiện hàng" : "Chưa tìm thấy kiện hàng",
                found
                        ? "Kỹ thuật viên Lock.R đã tìm thấy kiện hàng của đơn " + order.getOrderCode()
                                + " bị rơi trong lúc bay."
                        : "Kỹ thuật viên Lock.R chưa tìm thấy kiện hàng của đơn " + order.getOrderCode()
                                + " bị rơi trong lúc bay.",
                details,
                "Bạn theo dõi sự cố trong ứng dụng Lock.R: mở đơn " + order.getOrderCode() + " ⇒ Theo dõi drone.");
        boolean receiver = tellReceiver && sendNotice(order.getReceiverEmail(),
                nameOr(order.getReceiverName(), "bạn"), order,
                found
                        ? "Đã tìm thấy kiện hàng gửi cho bạn (" + order.getOrderCode() + ")"
                        : "Chưa tìm thấy kiện hàng gửi cho bạn (" + order.getOrderCode() + ")",
                found ? "Đã tìm thấy kiện hàng" : "Chưa tìm thấy kiện hàng",
                "Cập nhật sự cố của đơn " + order.getOrderCode() + " do "
                        + nameOr(order.getSenderName(), "người gửi") + " gửi cho bạn.",
                List.of(
                        new DroneDeliveryEmailTemplate.Detail("Mã sự cố", incidentCode),
                        new DroneDeliveryEmailTemplate.Detail("Kết quả", found ? "Đã tìm thấy kiện" : "Chưa tìm thấy kiện")),
                "Người gửi sẽ thống nhất cách xử lý với Lock.R; Lock.R báo lại bạn khi có kết quả.");
        return new Outcome(sender, receiver);
    }

    /// "14:05 30/10/2026" giờ Việt Nam.
    static String displayTime(Instant at) {
        return INCIDENT_TIME_FORMAT.format((at == null ? Instant.now() : at).atZone(DISPLAY_ZONE));
    }

    /// Đơn bị tạm dừng khi drone đã phóng (trời xấu trên tuyến, drone báo lỗi giữa chuyến, nắp tủ nhận lỗi).
    public Outcome deliveryPaused(LockerOrder order, String reasonLabel, String note, boolean tellReceiver) {
        String why = reasonLabel + (StringUtils.hasText(note) ? " — " + note.trim() : "");
        boolean sender = sendNotice(order.getSenderEmail(), nameOr(order.getSenderName(), "bạn"), order,
                "Đơn hàng " + order.getOrderCode() + " đang tạm dừng do sự cố",
                "Đơn hàng đang tạm dừng",
                "Đơn hàng " + order.getOrderCode() + " gặp sự cố trên đường giao và đang được đội bay xử lý.",
                List.of(
                        new DroneDeliveryEmailTemplate.Detail("Sự cố", why),
                        new DroneDeliveryEmailTemplate.Detail("Đang xử lý",
                                "Đội bay theo dõi drone, cho hạ cánh an toàn hoặc quay về trạm nếu cần.")),
                "Lock.R sẽ báo lại khi đơn tiếp tục hoặc có hướng xử lý khác.");
        boolean receiver = tellReceiver && sendNotice(order.getReceiverEmail(),
                nameOr(order.getReceiverName(), "bạn"), order,
                "Đơn hàng " + order.getOrderCode() + " gửi cho bạn đang tạm dừng",
                "Đơn hàng gửi cho bạn đang tạm dừng",
                "Đơn hàng " + order.getOrderCode() + " do " + nameOr(order.getSenderName(), "người gửi")
                        + " gửi cho bạn gặp sự cố trên đường giao.",
                List.of(new DroneDeliveryEmailTemplate.Detail("Sự cố", why)),
                "Lock.R sẽ báo lại khi đơn tiếp tục hoặc có hướng xử lý khác.");
        return new Outcome(sender, receiver);
    }

    /// Sự cố của đơn đang tạm dừng đã hết — đơn đi tiếp.
    public Outcome deliveryResumed(LockerOrder order, String clearedLabel, boolean tellReceiver) {
        List<DroneDeliveryEmailTemplate.Detail> details = List.of(
                new DroneDeliveryEmailTemplate.Detail("Sự cố đã xử lý", clearedLabel));
        boolean sender = sendNotice(order.getSenderEmail(), nameOr(order.getSenderName(), "bạn"), order,
                "Đơn hàng " + order.getOrderCode() + " tiếp tục được giao",
                "Đơn hàng tiếp tục được giao",
                "Sự cố ảnh hưởng tới đơn " + order.getOrderCode() + " đã được khắc phục, đơn tiếp tục được giao.",
                details, "Bạn theo dõi hành trình trong ứng dụng Lock.R.");
        boolean receiver = tellReceiver && sendNotice(order.getReceiverEmail(),
                nameOr(order.getReceiverName(), "bạn"), order,
                "Đơn hàng " + order.getOrderCode() + " gửi cho bạn tiếp tục được giao",
                "Đơn hàng tiếp tục được giao",
                "Sự cố ảnh hưởng tới đơn " + order.getOrderCode() + " đã được khắc phục.",
                details, "Khi hàng vào tủ, bạn sẽ nhận email có mã mở ô và thời hạn lấy hàng.");
        return new Outcome(sender, receiver);
    }

    /// Lock.R gửi phương án xử lý sự cố rơi kiện — người gửi phải vào ứng dụng đồng ý hoặc yêu cầu xem xét.
    public Outcome incidentProposal(LockerOrder order, String incidentCode, String resolutionLabel,
            String parcelCondition, String compensation, String note) {
        List<DroneDeliveryEmailTemplate.Detail> details = new ArrayList<>();
        details.add(new DroneDeliveryEmailTemplate.Detail("Mã sự cố", incidentCode));
        if (StringUtils.hasText(parcelCondition)) {
            details.add(new DroneDeliveryEmailTemplate.Detail("Tình trạng kiện", parcelCondition));
        }
        details.add(new DroneDeliveryEmailTemplate.Detail("Phương án", resolutionLabel));
        if (StringUtils.hasText(compensation)) {
            details.add(new DroneDeliveryEmailTemplate.Detail("Bồi thường", compensation));
        }
        if (StringUtils.hasText(note)) {
            details.add(new DroneDeliveryEmailTemplate.Detail("Nội dung", note.trim()));
        }
        boolean sender = sendNotice(order.getSenderEmail(), nameOr(order.getSenderName(), "bạn"), order,
                "Phương án xử lý sự cố đơn " + order.getOrderCode(),
                "Lock.R đã gửi phương án xử lý",
                "Lock.R đã kiểm tra sự cố rơi kiện của đơn " + order.getOrderCode() + " và gửi phương án xử lý.",
                details,
                "Mở ứng dụng Lock.R, mục chi tiết đơn hàng, để Đồng ý hoặc Yêu cầu xem xét phương án này.");
        return new Outcome(sender, false);
    }

    /// Người gửi đã đồng ý phương án: báo người nhận kiện có được giao lại không (người gửi nhận thư xác nhận).
    public Outcome incidentResolutionAccepted(LockerOrder order, String incidentCode, String resolutionLabel,
            String redeliveryOrderCode, boolean tellReceiver) {
        boolean redelivery = StringUtils.hasText(redeliveryOrderCode);
        String next = redelivery
                ? "Kiện được giao lại miễn phí bằng đơn mới " + redeliveryOrderCode
                        + "; khi drone phóng sẽ có email báo đơn đang trên đường."
                : "Kiện không được giao lại.";
        boolean sender = sendNotice(order.getSenderEmail(), nameOr(order.getSenderName(), "bạn"), order,
                "Đã ghi nhận phương án xử lý đơn " + order.getOrderCode(),
                "Đã ghi nhận phương án bạn chọn",
                "Lock.R đã ghi nhận bạn đồng ý phương án xử lý sự cố của đơn " + order.getOrderCode() + ".",
                List.of(
                        new DroneDeliveryEmailTemplate.Detail("Mã sự cố", incidentCode),
                        new DroneDeliveryEmailTemplate.Detail("Phương án", resolutionLabel),
                        new DroneDeliveryEmailTemplate.Detail("Tiếp theo", next)),
                "Tiền bồi thường (nếu có) được chuyển về tài khoản ngân hàng bạn đã lưu trong ứng dụng.");
        boolean receiver = tellReceiver && sendNotice(order.getReceiverEmail(),
                nameOr(order.getReceiverName(), "bạn"), order,
                redelivery
                        ? "Đơn hàng gửi cho bạn sẽ được giao lại (" + redeliveryOrderCode + ")"
                        : "Đơn hàng " + order.getOrderCode() + " gửi cho bạn sẽ không được giao",
                redelivery ? "Đơn hàng sẽ được giao lại" : "Đơn hàng không được giao",
                redelivery
                        ? "Đơn hàng " + order.getOrderCode() + " gặp sự cố sẽ được giao lại cho bạn bằng đơn "
                                + redeliveryOrderCode + "."
                        : "Đơn hàng " + order.getOrderCode() + " do " + nameOr(order.getSenderName(), "người gửi")
                                + " gửi cho bạn gặp sự cố và sẽ không được giao. Người gửi đã thống nhất cách xử "
                                + "lý với Lock.R.",
                List.of(new DroneDeliveryEmailTemplate.Detail("Mã sự cố", incidentCode)),
                redelivery
                        ? "Khi hàng vào tủ, bạn sẽ nhận email có mã mở ô mới và thời hạn lấy hàng."
                        : "Vui lòng liên hệ người gửi (" + nameOr(order.getSenderPhone(), "—") + ") nếu cần.");
        return new Outcome(sender, receiver);
    }

    /// Thư cập nhật sự cố: bản HTML theo template, bản chữ trơn dựng từ cùng nội dung.
    private boolean sendNotice(String email, String recipientName, LockerOrder order, String subject,
            String headline, String introduction, List<DroneDeliveryEmailTemplate.Detail> details, String footer) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        StringBuilder body = new StringBuilder("Xin chào ").append(recipientName).append(",\n\n")
                .append(introduction).append("\n\n");
        for (DroneDeliveryEmailTemplate.Detail detail : details) {
            body.append(detail.label()).append(": ").append(detail.value()).append('\n');
        }
        body.append('\n').append(footer).append('\n');
        String html = DroneDeliveryEmailTemplate.incident(
                recipientName, order.getOrderCode(), headline, introduction, details, footer);
        return send(email, subject, body.toString(), html);
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
