package com.huynqb.laundrylocker.assistant.orders;

import com.huynqb.laundrylocker.assistant.orders.OrderDtos.IncomingOrder;
import com.huynqb.laundrylocker.assistant.orders.OrderDtos.LockerInfo;
import com.huynqb.laundrylocker.assistant.orders.OrderDtos.MyOrder;
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator.ContextDocument;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/// Dựng tài liệu "Đơn hàng của bạn" (văn bản thuần, tiếng Việt) từ dữ liệu đơn của người hỏi.
///
/// Chỉ ghi những gì cần để trả lời về trạng thái, thanh toán, thời hạn và tủ. Không bao giờ có mã
/// PIN/QR, mã lấy hàng/mở ô, mã tham chiếu thanh toán, token, email hay số điện thoại đầy đủ — các
/// DTO đọc vào ([OrderDtos]) vốn không có những trường đó; số điện thoại người nhận chỉ còn 3 số cuối.
public final class OrderDocumentFormatter {

    public static final String TITLE = "Đơn hàng của bạn";
    /// Tổng số đơn đưa vào tài liệu (ngoài đơn đang hoạt động và đơn được nhắc mã).
    static final int MAX_ORDERS = 15;
    /// Trần cứng cho đơn đang hoạt động — luôn đưa vào, nhưng không để tài liệu phình vô hạn.
    static final int MAX_ACTIVE = 30;

    private static final Set<String> ACTIVE_STATUSES = Set.of("INITIALIZED", "AWAITING_DISPATCH", "STORING", "RETURNED");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    /// Một đơn trong tài liệu: đơn người hỏi tạo (`mine`) hoặc đơn người khác gửi cho họ (`incoming`).
    public record Entry(MyOrder mine, IncomingOrder incoming) {

        Long id() {
            return mine != null ? mine.id() : incoming.id();
        }

        String orderCode() {
            return mine != null ? mine.orderCode() : incoming.orderCode();
        }

        String status() {
            return mine != null ? mine.status() : incoming.status();
        }

        LocalDateTime createdAt() {
            return mine != null ? mine.createdAt() : incoming.createdAt();
        }

        boolean active() {
            String status = status();
            return status != null && ACTIVE_STATUSES.contains(status.toUpperCase(Locale.ROOT));
        }
    }

    /// Kết quả tải dữ liệu. `mine`/`incoming` null ⇒ không tải được nguồn đó.
    public record Snapshot(
            List<MyOrder> mine,
            List<IncomingOrder> incoming,
            boolean incomingMayHaveMore,
            List<Entry> selected,
            Map<Long, LockerInfo> lockers,
            Map<Long, Integer> boxNumbers,
            Set<String> mentionedCodes) {
    }

    private final ZoneId storageZone;
    private final ZoneId businessZone;

    /// `storageZone`: múi giờ của thời gian "trần" trong JSON order-service (múi giờ JVM — UTC trên
    /// production); `businessZone`: giờ hiển thị (Việt Nam).
    public OrderDocumentFormatter(ZoneId storageZone, ZoneId businessZone) {
        this.storageZone = storageZone;
        this.businessZone = businessZone;
    }

    /// Chọn đơn đưa vào tài liệu: đơn được nhắc mã, mọi đơn đang hoạt động, rồi thêm đơn gần nhất cho
    /// đủ [#MAX_ORDERS]; trùng id (tự gửi cho mình) giữ bản "đơn bạn tạo". Kết quả mới nhất trước.
    public static List<Entry> select(List<MyOrder> mine, List<IncomingOrder> incoming, Set<String> mentionedCodes) {
        List<Entry> all = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (MyOrder order : mine == null ? List.<MyOrder>of() : mine) {
            if (order != null && (order.id() == null || seen.add(order.id()))) {
                all.add(new Entry(order, null));
            }
        }
        for (IncomingOrder order : incoming == null ? List.<IncomingOrder>of() : incoming) {
            if (order != null && (order.id() == null || seen.add(order.id()))) {
                all.add(new Entry(null, order));
            }
        }
        Comparator<Entry> newestFirst = Comparator.comparing(
                Entry::createdAt, Comparator.nullsLast(Comparator.reverseOrder()));
        all.sort(newestFirst);
        Set<Entry> picked = new LinkedHashSet<>();
        for (Entry entry : all) {
            if (entry.orderCode() != null && mentionedCodes.contains(entry.orderCode().toUpperCase(Locale.ROOT))) {
                picked.add(entry);
            }
        }
        int active = 0;
        for (Entry entry : all) {
            if (entry.active() && active < MAX_ACTIVE) {
                picked.add(entry);
                active++;
            }
        }
        for (Entry entry : all) {
            if (picked.size() >= MAX_ORDERS) {
                break;
            }
            picked.add(entry);
        }
        List<Entry> result = new ArrayList<>(picked);
        result.sort(newestFirst);
        return result;
    }

    public ContextDocument format(Snapshot snapshot, Instant now) {
        String fetchedAt = TIME.format(now.atZone(businessZone));
        StringBuilder out = new StringBuilder();
        out.append("Dữ liệu lúc ").append(fetchedAt)
                .append(" (giờ Việt Nam), lấy trực tiếp từ hệ thống cho chính người đang hỏi.\n");
        out.append(summary(snapshot)).append('\n');
        out.append("Dữ liệu này không có mã PIN, mã QR hay mã lấy hàng/mở ô; mã của đơn chỉ xem được trong mục "
                + "\"Đơn hàng\" của ứng dụng Lock.R.\n");
        Set<String> listedCodes = new HashSet<>();
        List<Entry> active = snapshot.selected().stream().filter(Entry::active).toList();
        List<Entry> finished = snapshot.selected().stream().filter(entry -> !entry.active()).toList();
        int index = 1;
        if (!active.isEmpty()) {
            out.append("\nĐƠN ĐANG HOẠT ĐỘNG\n");
            for (Entry entry : active) {
                appendEntry(out, index++, entry, snapshot, now);
                listedCodes.add(upper(entry.orderCode()));
            }
        }
        if (!finished.isEmpty()) {
            out.append("\nĐƠN ĐÃ KẾT THÚC GẦN ĐÂY\n");
            for (Entry entry : finished) {
                appendEntry(out, index++, entry, snapshot, now);
                listedCodes.add(upper(entry.orderCode()));
            }
        }
        for (String code : snapshot.mentionedCodes()) {
            if (!listedCodes.contains(code)) {
                out.append("\nKhông thấy đơn ").append(code)
                        .append(" trong các đơn của người hỏi (đơn bạn tạo và đơn người khác gửi cho bạn).\n");
            }
        }
        return new ContextDocument(TITLE, "Dữ liệu lúc " + fetchedAt + " (giờ Việt Nam)", out.toString().trim());
    }

    private String summary(Snapshot snapshot) {
        List<String> parts = new ArrayList<>();
        if (snapshot.mine() == null) {
            parts.add("Không tải được danh sách đơn bạn đã tạo lúc này");
        } else {
            parts.add(snapshot.mine().isEmpty() ? "Bạn chưa tạo đơn nào" : "Bạn đã tạo " + snapshot.mine().size() + " đơn");
        }
        if (snapshot.incoming() == null) {
            parts.add("không tải được danh sách hàng người khác gửi cho bạn lúc này");
        } else if (snapshot.incoming().isEmpty()) {
            parts.add("chưa có đơn nào người khác gửi cho bạn");
        } else {
            parts.add("có " + (snapshot.incomingMayHaveMore() ? "ít nhất " : "") + snapshot.incoming().size()
                    + " đơn người khác gửi cho bạn");
        }
        String line = String.join("; ", parts) + ".";
        if (!snapshot.selected().isEmpty()) {
            line += " Dưới đây là " + snapshot.selected().size()
                    + " đơn: mọi đơn đang hoạt động và các đơn gần nhất, mới nhất trước.";
        }
        return line;
    }

    private void appendEntry(StringBuilder out, int index, Entry entry, Snapshot snapshot, Instant now) {
        if (entry.mine() != null) {
            appendMine(out, index, entry.mine(), snapshot, now, entry.active());
        } else {
            appendIncoming(out, index, entry.incoming(), now, entry.active());
        }
        out.append('\n');
    }

    private void appendMine(StringBuilder out, int index, MyOrder order, Snapshot snapshot, Instant now, boolean active) {
        String type = upper(order.type());
        out.append(index).append(". Mã đơn ").append(orElse(order.orderCode(), "(không rõ)"))
                .append(" · ").append(typeLabel(type)).append(" · ").append(ownerLabel(type)).append('\n');
        line(out, "Trạng thái", statusWithStage(type, order.status(), order.deliveryStage()));
        line(out, "Thanh toán", payment(order));
        Long boxId = order.receiveBoxId() != null ? order.receiveBoxId() : order.sendBoxId();
        LockerInfo locker = order.lockerId() == null ? null : snapshot.lockers().get(order.lockerId());
        line(out, "DRONE_DELIVERY".equals(type) ? "Tủ nhận" : "Tủ",
                locker(locker == null ? null : locker.name(), locker == null ? null : locker.address(),
                        boxId == null ? null : snapshot.boxNumbers().get(boxId)));
        line(out, "Tạo lúc", time(order.createdAt()));
        if ("RENTAL".equals(type) && order.rentalDurationHours() != null) {
            line(out, "Thời lượng thuê", order.rentalDurationHours() + " giờ");
        }
        line(out, "RENTAL".equals(type) ? "Giờ hết thuê" : "Hạn lấy hàng", deadline(order.pickupDeadline(), now, active));
        if (isPositive(order.pickupOvertimeFee())) {
            line(out, "Phí quá hạn hiện tại", money(order.pickupOvertimeFee()));
        }
        line(out, "Hoàn tất lúc", time(order.completedAt()));
        if (!"RENTAL".equals(type) && StringUtils.hasText(order.receiverName())) {
            String phone = maskedPhone(order.receiverPhone());
            line(out, "Người nhận", order.receiverName().trim() + (phone == null ? "" : " (SĐT đuôi " + phone + ")"));
        }
        line(out, "Mở ô bằng mã", blockReason(order.accessBlockReason()));
        if (active) {
            line(out, "Bước tiếp theo", nextAction(type, order.nextAction()));
        }
    }

    private void appendIncoming(StringBuilder out, int index, IncomingOrder order, Instant now, boolean active) {
        String type = upper(order.type());
        out.append(index).append(". Mã đơn ").append(orElse(order.orderCode(), "(không rõ)"))
                .append(" · ").append(typeLabel(type)).append(" · người khác gửi cho bạn\n");
        line(out, "Người gửi", StringUtils.hasText(order.senderName()) ? order.senderName().trim() : null);
        line(out, "Trạng thái", statusWithStage(type, order.status(), order.deliveryStage()));
        line(out, "Thanh toán", paymentStatusLabel(order.paymentStatus()));
        line(out, "Tủ", locker(order.lockerName(), order.lockerAddress(), order.boxNumber()));
        line(out, "Tạo lúc", time(order.createdAt()));
        line(out, "Hạn lấy hàng", deadline(order.pickupDeadline(), now, active));
        if (active && order.readyForPickup()) {
            line(out, "Lấy hàng", "Hàng đã nằm trong ô, mở được bằng mã của đơn (xem trong mục \"Hàng chờ nhận\")");
        }
        if (order.delegated()) {
            line(out, "Ủy quyền", "Đơn đã được ủy quyền cho người khác lấy hộ");
        }
        line(out, "Hoàn tất lúc", time(order.completedAt()));
    }

    // ---- Nhãn ----

    static String typeLabel(String type) {
        return switch (type) {
            case "SEND" -> "Gửi hàng";
            case "RENTAL" -> "Thuê ô";
            case "STORAGE" -> "Gửi đồ";
            case "DRONE_DELIVERY" -> "Giao bằng drone";
            case "LAUNDRY" -> "Giặt ủi";
            case "PARCEL" -> "Nhận hàng";
            default -> type.isEmpty() ? "Đơn" : type;
        };
    }

    private static String ownerLabel(String type) {
        return switch (type) {
            case "RENTAL" -> "bạn thuê";
            case "SEND", "DRONE_DELIVERY" -> "bạn gửi";
            default -> "bạn tạo";
        };
    }

    static String statusLabel(String type, String status) {
        String value = upper(status);
        return switch (value) {
            case "INITIALIZED" -> "RENTAL".equals(type)
                    ? "Đã đặt ô, chưa bắt đầu thuê (chờ thanh toán và bỏ đồ vào ô)"
                    : "Chờ bỏ hàng vào ô";
            case "AWAITING_DISPATCH" -> "Đang giao bằng drone";
            case "STORING" -> switch (type) {
                case "RENTAL" -> "Đang thuê, đồ đang trong ô";
                case "DRONE_DELIVERY" -> "Hàng đã tới tủ nhận, đang nằm trong ô";
                default -> "Hàng đang nằm trong ô, chờ người nhận lấy";
            };
            case "COMPLETED" -> switch (type) {
                case "RENTAL" -> "Đã kết thúc thuê";
                case "SEND", "DRONE_DELIVERY" -> "Hoàn tất — hàng đã được lấy khỏi ô";
                default -> "Hoàn tất";
            };
            case "CANCELED", "CANCELLED" -> "Đã huỷ";
            case "EXPIRED" -> "Quá hạn — hàng đã được chuyển khỏi ô, cần liên hệ nhân viên Lock.R để nhận lại";
            case "RETURNED" -> "Chờ lấy";
            default -> value.isEmpty() ? "Không rõ" : value;
        };
    }

    static String droneStageLabel(String stage) {
        return switch (upper(stage)) {
            case "AWAITING_DISPATCH" -> "chờ kỹ thuật viên tiếp nhận";
            case "PICKUP_ASSIGNED" -> "kỹ thuật viên đang tới lấy hàng";
            case "PICKED_UP" -> "đã lấy hàng, đang mang về tủ gửi";
            case "ACCEPTED", "LOADING_CONFIRMED" -> "đang nạp hàng lên drone";
            case "LAUNCHING" -> "drone đang khởi phóng";
            case "DEPARTED", "DISPATCHED" -> "drone đã rời trạm";
            case "EN_ROUTE" -> "drone đang bay tới tủ nhận";
            case "APPROACHING" -> "drone sắp đến tủ nhận";
            case "ARRIVED" -> "drone đã đến tủ nhận";
            case "READY_FOR_PICKUP", "DELIVERED" -> "hàng đã trong tủ nhận, sẵn sàng lấy";
            case "DELAYED" -> "bị chậm so với dự kiến";
            case "FAILED" -> "giao không hoàn tất";
            case "DROP_REPORTED" -> "kiện gặp sự cố rơi trong chuyến bay, Lock.R đang xử lý";
            case "CANCELED", "CANCELLED" -> "đã huỷ";
            case "COMPLETED" -> "đã nhận hàng";
            case "EXPIRED" -> "quá hạn nhận hàng";
            default -> null;
        };
    }

    private static String statusWithStage(String type, String status, String stage) {
        String label = statusLabel(type, status);
        String stageLabel = "DRONE_DELIVERY".equals(type) ? droneStageLabel(stage) : null;
        return stageLabel == null ? label : label + " (chặng drone: " + stageLabel + ")";
    }

    static String paymentStatusLabel(String status) {
        return switch (upper(status)) {
            case "PAID" -> "Đã thanh toán";
            case "UNPAID" -> "Chưa thanh toán";
            case "REFUND_PENDING" -> "Đang chờ hoàn tiền";
            case "REFUNDED" -> "Đã hoàn tiền";
            case "" -> null;
            default -> status;
        };
    }

    private static String payment(MyOrder order) {
        String status = upper(order.paymentStatus());
        BigDecimal total = order.totalPrice();
        BigDecimal paid = order.paidAmount();
        BigDecimal due = order.amountDue();
        if (!isPositive(total) && !isPositive(paid) && !status.startsWith("REFUND")) {
            return "Không mất phí (0đ)";
        }
        return switch (status) {
            case "PAID" -> isPositive(due)
                    ? "Đã trả " + money(paid) + ", còn phải trả thêm " + money(due) + " (tổng " + money(total) + ")"
                    : "Đã thanh toán " + money(isPositive(paid) ? paid : total);
            case "UNPAID" -> "Chưa thanh toán — cần trả " + money(isPositive(due) ? due : total)
                    + (isPositive(paid) ? " (đã trả trước " + money(paid) + ")" : "");
            case "REFUNDED" -> "Đã hoàn tiền" + (isPositive(paid) ? " (" + money(paid) + ")" : "");
            case "REFUND_PENDING" -> "Đang chờ hoàn tiền";
            default -> paymentStatusLabel(order.paymentStatus());
        };
    }

    static String nextAction(String type, String action) {
        return switch (upper(action)) {
            case "PAY_AND_DROP" -> "RENTAL".equals(type)
                    ? "Thanh toán rồi bỏ đồ vào ô để bắt đầu thuê"
                    : "Thanh toán rồi bỏ hàng vào ô";
            case "PICKUP" -> switch (type) {
                case "RENTAL" -> "Đang thuê: có thể mở ô lấy đồ hoặc kết thúc thuê tại tủ";
                case "SEND" -> "Chờ người nhận tới tủ lấy hàng";
                case "DRONE_DELIVERY" -> "Người nhận lấy hàng tại tủ nhận";
                default -> "Lấy hàng tại tủ";
            };
            case "WAIT_FOR_DRONE" -> "Chờ đội bay tiếp nhận và phóng drone";
            case "WAIT_FOR_PICKUP" -> "Chờ kỹ thuật viên drone tới địa chỉ lấy hàng";
            case "DROP_PARCEL_FOR_DRONE" -> "Bỏ kiện vào ô drone ở tủ gửi rồi xác nhận đã bỏ";
            case "PAY_FOR_DRONE" -> "Thanh toán để đội bay điều phối giao hàng";
            case "PAY_BEFORE_PICKUP" -> "Thanh toán trước khi mở ô lấy hàng";
            case "INCIDENT_IN_PROGRESS" -> "Lock.R đang xử lý sự cố giao hàng, theo dõi ở mục sự cố của đơn";
            case "WAIT_FOR_BOX_INCIDENT" -> "Chờ KTV xử lý sự cố ô; đồ vẫn trong ô, chưa cần thanh toán";
            case "CONTACT_STAFF" -> "Liên hệ nhân viên Lock.R để nhận lại hàng";
            default -> null;
        };
    }

    static String blockReason(String reason) {
        return switch (upper(reason)) {
            case "ORDER_UNPAID" -> "Chưa mở được: đơn chưa thanh toán";
            case "RENTAL_EXPIRED" -> "Chưa mở được: đã hết giờ thuê, cần thanh toán phí quá giờ";
            case "RENTAL_UNPAID" -> "Chưa mở được: phần gia hạn chưa thanh toán";
            case "BOX_INCIDENT_OPEN" -> "Chưa mở được: ô đang chờ KTV xử lý sự cố, phí quá giờ tạm dừng";
            default -> null;
        };
    }

    // ---- Định dạng ----

    private static void line(StringBuilder out, String label, String value) {
        if (StringUtils.hasText(value)) {
            out.append("- ").append(label).append(": ").append(value).append('\n');
        }
    }

    private static String locker(String name, String address, Integer boxNumber) {
        StringBuilder text = new StringBuilder();
        if (StringUtils.hasText(name)) {
            text.append(name.trim());
        }
        if (StringUtils.hasText(address)) {
            text.append(text.isEmpty() ? "" : " — ").append(address.trim());
        }
        if (boxNumber != null) {
            text.append(text.isEmpty() ? "" : " · ").append("ô số ").append(boxNumber);
        }
        return text.toString();
    }

    private String time(LocalDateTime stored) {
        if (stored == null) {
            return null;
        }
        return TIME.format(stored.atZone(storageZone).withZoneSameInstant(businessZone));
    }

    /// Thời điểm + "còn …"/"đã quá …" (tính sẵn để mô hình khỏi tự trừ giờ) khi đơn còn hoạt động.
    private String deadline(LocalDateTime stored, Instant now, boolean active) {
        String formatted = time(stored);
        if (formatted == null || !active) {
            return formatted;
        }
        Duration left = Duration.between(now, stored.atZone(storageZone).toInstant());
        return formatted + (left.isNegative()
                ? " (đã quá " + duration(left.negated()) + ")"
                : " (còn " + duration(left) + ")");
    }

    static String duration(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes < 1) {
            return "dưới 1 phút";
        }
        long days = minutes / (60 * 24);
        long hours = (minutes / 60) % 24;
        long mins = minutes % 60;
        List<String> parts = new ArrayList<>();
        if (days > 0) {
            parts.add(days + " ngày");
        }
        if (hours > 0) {
            parts.add(hours + " giờ");
        }
        if (mins > 0 && days == 0) {
            parts.add(mins + " phút");
        }
        return parts.isEmpty() ? days + " ngày" : String.join(" ", parts);
    }

    static String money(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount.setScale(0, RoundingMode.HALF_UP);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        return new DecimalFormat("#,##0", symbols).format(value) + "đ";
    }

    /// 3 số cuối; ít hơn 7 chữ số thì coi như không phải số điện thoại và bỏ hẳn.
    static String maskedPhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        return digits.length() < 7 ? null : digits.substring(digits.length() - 3);
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String orElse(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
