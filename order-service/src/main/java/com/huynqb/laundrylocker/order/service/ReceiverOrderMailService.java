package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.util.BusinessTime;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.GuestNotification;
import com.huynqb.laundrylocker.order.dto.ReceiverOrderDetailResponse;
import com.huynqb.laundrylocker.order.dto.ReceiverOrderEmailResponse;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/// Người nhận (hoặc người lấy hộ) bấm "Gửi lại qua email" trong chi tiết đơn nhận: gửi thông tin đơn tới
/// email của chính họ — mã đơn, người gửi, tủ/ô, trạng thái, và mã mở tủ + hạn lấy khi hàng đã vào tủ.
///
/// Quyền xem theo đúng [ReceiverOrderQueryService#detail] (đơn không thuộc họ ⇒ 404), nên email không bao giờ
/// chứa mã mà màn chi tiết không cho họ thấy. Mỗi đơn chỉ gửi lại một lần mỗi [#COOLDOWN].
@Slf4j
@Service
public class ReceiverOrderMailService {

    static final Duration COOLDOWN = Duration.ofSeconds(60);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final ReceiverOrderQueryService receiverOrders;
    private final UserClient userClient;
    private final NotificationClient notificationClient;
    private final OrderStatusHistoryRepository historyRepository;
    private final Map<String, Instant> lastSent = new ConcurrentHashMap<>();

    private Clock clock = Clock.systemUTC();
    private BusinessTime time = BusinessTime.system();

    @Autowired
    public ReceiverOrderMailService(
            ReceiverOrderQueryService receiverOrders,
            UserClient userClient,
            NotificationClient notificationClient,
            OrderStatusHistoryRepository historyRepository) {
        this.receiverOrders = receiverOrders;
        this.userClient = userClient;
        this.notificationClient = notificationClient;
        this.historyRepository = historyRepository;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    void setTime(BusinessTime time) {
        this.time = time;
    }

    public ReceiverOrderEmailResponse resend(Long userId, Long orderId) {
        ReceiverOrderDetailResponse order = receiverOrders.detail(userId, orderId);
        String email = recipient(userId, order);
        if (!StringUtils.hasText(email)) {
            throw new BusinessException("RECEIVER_EMAIL_MISSING",
                    "Tài khoản của bạn chưa có email. Bổ sung email trong Hồ sơ rồi thử lại.");
        }

        String key = userId + ":" + order.id();
        Instant now = clock.instant();
        Instant previous = lastSent.get(key);
        if (previous != null && previous.plus(COOLDOWN).isAfter(now)) {
            long wait = Duration.between(now, previous.plus(COOLDOWN)).toSeconds() + 1;
            throw new BusinessException("RECEIVER_EMAIL_TOO_SOON",
                    "Vừa gửi email cho đơn này. Thử lại sau " + wait + " giây.", HttpStatus.TOO_MANY_REQUESTS);
        }

        boolean sent;
        try {
            var response = notificationClient.notifyGuest(new GuestNotification.Request(
                    null, email, "Thông tin đơn " + order.orderCode() + " trên Lock.R", null, emailBody(order)));
            GuestNotification.Result result = response == null ? null : response.data();
            sent = result != null && result.emailSent();
        } catch (Exception ex) {
            log.warn("Could not resend order {} info to the receiver: {}", order.id(), ex.getMessage());
            sent = false;
        }
        if (!sent) {
            throw new BusinessException("RECEIVER_EMAIL_FAILED",
                    "Chưa gửi được email lúc này. Thử lại sau ít phút.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        lastSent.put(key, now);

        String masked = DroneMissionProgressService.maskEmail(email);
        record(order, userId, "Người nhận yêu cầu gửi lại thông tin đơn qua email " + masked + ".");
        return new ReceiverOrderEmailResponse(masked, order.pickupPin() != null, LocalDateTime.now(clock));
    }

    /// Email tài khoản của người bấm; người nhận chưa có email tài khoản thì dùng email người gửi đã nhập cho họ.
    private String recipient(Long userId, ReceiverOrderDetailResponse order) {
        String account = null;
        try {
            var user = userClient.getUser(userId);
            account = user == null || user.data() == null ? null : user.data().email();
        } catch (Exception ex) {
            log.debug("Account email of user {} not resolved: {}", userId, ex.getMessage());
        }
        if (StringUtils.hasText(account)) {
            return account.trim();
        }
        boolean receiver = ReceiverOrderQueryService.VIEWER_RECEIVER.equals(order.viewerRole());
        return receiver && StringUtils.hasText(order.receiverEmail()) ? order.receiverEmail().trim() : null;
    }

    String emailBody(ReceiverOrderDetailResponse order) {
        StringBuilder body = new StringBuilder("Xin chào")
                .append(StringUtils.hasText(order.receiverName()) ? " " + order.receiverName().trim() : "")
                .append(",\n\nĐây là thông tin đơn hàng bạn yêu cầu gửi lại từ ứng dụng Lock.R.\n\n")
                .append("Mã đơn: ").append(order.orderCode()).append("\n")
                .append("Loại đơn: ").append(typeLabel(order.type())).append("\n");
        if (StringUtils.hasText(order.senderName())) {
            body.append("Người gửi: ").append(order.senderName().trim()).append("\n");
        }
        String place = place(order);
        if (place != null) {
            body.append("Nơi lấy: ").append(place).append("\n");
        }
        body.append("Trạng thái: ").append(statusLabel(order)).append("\n");
        if (order.pickupPin() != null) {
            body.append("Mã mở tủ: ").append(order.pickupPin()).append("\n");
            body.append("Hạn lấy hàng: ").append(format(order.pickupDeadline())).append("\n");
        }
        if (StringUtils.hasText(order.customerNote())) {
            body.append("Lời nhắn của người gửi: ").append(order.customerNote().trim()).append("\n");
        }
        body.append("\n");
        if (order.pickupPin() != null) {
            body.append("Nhập mã này trên màn hình tủ để mở ô và lấy hàng. Không chia sẻ mã cho người khác.\n");
        } else if (order.delegated() && ReceiverOrderQueryService.VIEWER_RECEIVER.equals(order.viewerRole())) {
            body.append("Bạn đã ủy quyền cho người khác lấy hộ đơn này, nên mã mở tủ được gửi cho người lấy hộ.\n");
        } else if (!order.readyForPickup()) {
            body.append("Mã mở tủ sẽ được gửi cho bạn khi hàng đã vào tủ.\n");
        }
        return body.append("\nNếu bạn không yêu cầu email này, hãy bỏ qua nó.\n").toString();
    }

    private static String place(ReceiverOrderDetailResponse order) {
        if (!StringUtils.hasText(order.lockerName())) {
            return null;
        }
        StringBuilder place = new StringBuilder(order.lockerName().trim());
        if (StringUtils.hasText(order.lockerAddress())) {
            place.append(" (").append(order.lockerAddress().trim()).append(")");
        }
        if (order.boxNumber() != null) {
            place.append(", ô số ").append(order.boxNumber());
        }
        return place.toString();
    }

    private static String typeLabel(String type) {
        return switch (type == null ? "" : type.toUpperCase()) {
            case "DRONE_DELIVERY" -> "Giao bằng drone";
            case "SEND" -> "Gửi hàng qua tủ";
            case "RENTAL" -> "Thuê tủ";
            default -> "Đơn tủ";
        };
    }

    private static String statusLabel(ReceiverOrderDetailResponse order) {
        if (order.readyForPickup()) {
            return order.overdue() ? "Đã quá hạn lấy hàng" : "Hàng đã vào tủ, chờ bạn lấy";
        }
        return switch (order.status() == null ? "" : order.status().toUpperCase()) {
            case "INITIALIZED" -> "Người gửi chưa bỏ hàng vào tủ";
            case "AWAITING_DISPATCH" -> "Đang giao tới tủ";
            case "COMPLETED" -> "Đã lấy hàng";
            case "CANCELED" -> "Đã hủy";
            case "EXPIRED" -> "Đã quá hạn";
            default -> "Đang xử lý";
        };
    }

    private String format(LocalDateTime value) {
        if (value == null) {
            return "chưa xác định";
        }
        return TIME_FORMAT.format(value.atZone(time.storageZone()).withZoneSameInstant(time.businessZone()));
    }

    private void record(ReceiverOrderDetailResponse order, Long userId, String note) {
        try {
            OrderStatusHistory history = new OrderStatusHistory();
            history.setOrderId(order.id());
            history.setOldStatus(order.status());
            history.setNewStatus(order.status());
            history.setChangedByUserId(userId);
            history.setNote(note);
            historyRepository.save(history);
        } catch (Exception ex) {
            log.debug("Could not record the resend note on order {}: {}", order.id(), ex.getMessage());
        }
    }
}
