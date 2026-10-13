package com.huynqb.laundrylocker.assistant.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.huynqb.laundrylocker.assistant.orders.OrderDocumentFormatter.Entry;
import com.huynqb.laundrylocker.assistant.orders.OrderDocumentFormatter.Snapshot;
import com.huynqb.laundrylocker.assistant.orders.OrderDtos.IncomingOrder;
import com.huynqb.laundrylocker.assistant.orders.OrderDtos.LockerInfo;
import com.huynqb.laundrylocker.assistant.orders.OrderDtos.MyOrder;
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator.ContextDocument;
import com.huynqb.laundrylocker.common.util.BusinessTime;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/// Thời gian "trần" của order-service là UTC (như production); tài liệu hiển thị giờ Việt Nam (+7).
class OrderDocumentFormatterTest {

    /// 14:05 13/10/2026 giờ Việt Nam.
    private static final Instant NOW = Instant.parse("2026-10-13T07:05:00Z");
    private static final ObjectMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    private final OrderDocumentFormatter formatter =
            new OrderDocumentFormatter(ZoneOffset.UTC, BusinessTime.DEFAULT_BUSINESS_ZONE);

    /// JSON y như order-service trả về, kể cả PIN/QR, SĐT, email, ghi chú: những trường đó phải biến mất
    /// ngay khi đọc vào DTO của trợ lý và không bao giờ xuất hiện trong tài liệu.
    @Test
    void neverIncludesCodesFullPhonesOrEmailsEvenWhenTheResponseCarriesThem() throws Exception {
        MyOrder mine = JSON.readValue("""
                {"id":11,"orderCode":"ORD-20261012-AB12","userId":7,"lockerId":3,"sendBoxId":31,"receiveBoxId":null,
                 "type":"SEND","status":"STORING","pinCode":"482913","qrToken":"qr-secret-token-abc",
                 "totalPrice":15000.00,"paidAmount":15000.00,"amountDue":0,"paymentStatus":"PAID",
                 "nextAction":"PICKUP","nextActionMessage":"Pick up items from locker.",
                 "pickupDeadline":"2026-10-14T09:12:00","createdAt":"2026-10-12T02:12:00",
                 "receiverName":"Nguyễn Văn A","receiverPhone":"0912 345 789","receiverEmail":"nhan@example.com",
                 "customerNote":"PIN là 482913, gọi 0912345789","deliveryAddress":"12 Nguyễn Trãi",
                 "promotionCode":"SALE50","accessBlockReason":null}
                """, MyOrder.class);
        IncomingOrder incoming = JSON.readValue("""
                {"id":21,"orderCode":"ORD-20261013-ZX9","type":"SEND","status":"STORING","senderUserId":9,
                 "senderName":"Trần Thị B","receiverName":"Tôi","receiverPhone":"0987654321",
                 "lockerId":4,"lockerCode":"CAB-TU01","lockerName":"Tủ Thư viện","lockerAddress":"Khu E2a",
                 "lockerLatitude":10.84,"lockerLongitude":106.80,"boxId":41,"boxNumber":5,
                 "pickupDeadline":"2026-10-15T01:00:00","overdue":false,"readyForPickup":true,
                 "pickupPin":"771234","qrToken":"qr-in-token","paymentStatus":"PAID","delegated":false,
                 "delegateName":"Lê C","delegateNote":"gọi 0911222333","createdAt":"2026-10-13T01:00:00"}
                """, IncomingOrder.class);

        ContextDocument document = format(List.of(mine), List.of(incoming),
                Map.of(3L, new LockerInfo(3L, "Tủ Ký túc xá", "Toà A")), Map.of(31L, 12), Set.of());
        String text = document.content();

        for (String secret : List.of("482913", "qr-secret-token-abc", "0912 345 789", "0912345789",
                "nhan@example.com", "SALE50", "12 Nguyễn Trãi", "771234", "qr-in-token", "0987654321",
                "0911222333", "Lê C", "CAB-TU01")) {
            assertFalse(text.contains(secret), "document must not contain " + secret);
        }
        assertEquals("Đơn hàng của bạn", document.title());
        assertEquals("Dữ liệu lúc 14:05 13/10/2026 (giờ Việt Nam)", document.context());
        assertTrue(text.startsWith("Dữ liệu lúc 14:05 13/10/2026 (giờ Việt Nam)"));
        assertTrue(text.contains("không có mã PIN, mã QR hay mã lấy hàng"));
        assertTrue(text.contains("Mã đơn ORD-20261012-AB12 · Gửi hàng · bạn gửi"));
        assertTrue(text.contains("- Trạng thái: Hàng đang nằm trong ô, chờ người nhận lấy"));
        assertTrue(text.contains("- Thanh toán: Đã thanh toán 15.000đ"));
        assertTrue(text.contains("- Tủ: Tủ Ký túc xá — Toà A · ô số 12"));
        assertTrue(text.contains("- Tạo lúc: 09:12 12/10/2026"));
        assertTrue(text.contains("- Hạn lấy hàng: 16:12 14/10/2026 (còn 1 ngày 2 giờ)"));
        assertTrue(text.contains("- Người nhận: Nguyễn Văn A (SĐT đuôi 789)"));
        assertTrue(text.contains("- Bước tiếp theo: Chờ người nhận tới tủ lấy hàng"));
        assertTrue(text.contains("Mã đơn ORD-20261013-ZX9 · Gửi hàng · người khác gửi cho bạn"));
        assertTrue(text.contains("- Người gửi: Trần Thị B"));
        assertTrue(text.contains("- Tủ: Tủ Thư viện — Khu E2a · ô số 5"));
        assertTrue(text.contains("mở được bằng mã của đơn (xem trong mục \"Hàng chờ nhận\")"));
        assertTrue(text.contains("Bạn đã tạo 1 đơn; có 1 đơn người khác gửi cho bạn."));
        // Mới nhất trước: đơn gửi tới (13/10) đứng trước đơn tự tạo (12/10).
        assertTrue(text.indexOf("ORD-20261013-ZX9") < text.indexOf("ORD-20261012-AB12"));
    }

    /// Chặn từ gốc: DTO của trợ lý không khai báo trường mã/token/liên hệ nào.
    @Test
    void dtosDeclareNoCodeOrContactFields() {
        for (Class<?> type : List.of(MyOrder.class, IncomingOrder.class)) {
            Arrays.stream(type.getRecordComponents()).map(component -> component.getName())
                    .forEach(name -> assertFalse(
                            name.matches("(?i).*(pin|qr|token|email|note|secret|password|reference).*"),
                            type.getSimpleName() + "." + name));
        }
    }

    @Test
    void rentalShowsEndTimeOverdueFeeAndWhyTheCodeIsBlocked() {
        MyOrder rental = mine(1L, "ORD-20261013-R1", "RENTAL", "STORING", "UNPAID",
                LocalDateTime.parse("2026-10-13T06:35:00"), LocalDateTime.parse("2026-10-13T02:35:00"));
        rental = new MyOrder(rental.id(), rental.orderCode(), "RENTAL", "STORING", null, 3L, 31L, null, "UNPAID",
                new BigDecimal("25000"), new BigDecimal("20000"), new BigDecimal("5000"), new BigDecimal("5000"), true,
                rental.pickupDeadline(), null, rental.createdAt(), null, null, 4, "PICKUP", "RENTAL_EXPIRED");

        String text = format(List.of(rental), List.of(), Map.of(), Map.of(), Set.of()).content();

        assertTrue(text.contains("Mã đơn ORD-20261013-R1 · Thuê ô · bạn thuê"));
        assertTrue(text.contains("- Trạng thái: Đang thuê, đồ đang trong ô"));
        assertTrue(text.contains("- Thời lượng thuê: 4 giờ"));
        assertTrue(text.contains("- Giờ hết thuê: 13:35 13/10/2026 (đã quá 30 phút)"));
        assertTrue(text.contains("- Thanh toán: Chưa thanh toán — cần trả 5.000đ (đã trả trước 20.000đ)"));
        assertTrue(text.contains("- Phí quá hạn hiện tại: 5.000đ"));
        assertTrue(text.contains("- Mở ô bằng mã: Chưa mở được: đã hết giờ thuê, cần thanh toán phí quá giờ"));
        assertFalse(text.contains("Người nhận"));
    }

    @Test
    void finishedOrdersAreListedSeparatelyWithoutCountdown() {
        MyOrder done = new MyOrder(2L, "ORD-20261012-D1", "SEND", "COMPLETED", null, null, null, null, "PAID",
                new BigDecimal("15000"), new BigDecimal("15000"), BigDecimal.ZERO, null, false,
                LocalDateTime.parse("2026-10-13T02:00:00"), LocalDateTime.parse("2026-10-12T10:30:00"),
                LocalDateTime.parse("2026-10-12T02:00:00"), "Bạn A", "12", null, "DONE", null);

        String text = format(List.of(done), List.of(), Map.of(), Map.of(), Set.of()).content();

        assertTrue(text.contains("ĐƠN ĐÃ KẾT THÚC GẦN ĐÂY"));
        assertFalse(text.contains("ĐƠN ĐANG HOẠT ĐỘNG"));
        assertTrue(text.contains("- Trạng thái: Hoàn tất — hàng đã được lấy khỏi ô"));
        assertTrue(text.contains("- Hoàn tất lúc: 17:30 12/10/2026"));
        assertTrue(text.contains("- Hạn lấy hàng: 09:00 13/10/2026\n"));
        assertFalse(text.contains("Bước tiếp theo"));
        // "12" không giống số điện thoại ⇒ bỏ hẳn.
        assertTrue(text.contains("- Người nhận: Bạn A"));
        assertFalse(text.contains("SĐT"));
    }

    @Test
    void droneOrderShowsStageAndDestinationLocker() {
        MyOrder drone = new MyOrder(3L, "ORD-20261013-DR", "DRONE_DELIVERY", "AWAITING_DISPATCH", "EN_ROUTE", 5L, 51L,
                null, "PAID", new BigDecimal("30000"), new BigDecimal("30000"), BigDecimal.ZERO, null, false, null,
                null, LocalDateTime.parse("2026-10-13T05:00:00"), "Phạm D", null, null, "WAIT_FOR_DRONE", null);

        String text = format(List.of(drone), List.of(), Map.of(5L, new LockerInfo(5L, "Tủ Cổng B", null)),
                Map.of(51L, 2), Set.of()).content();

        assertTrue(text.contains("· Giao bằng drone · bạn gửi"));
        assertTrue(text.contains("- Trạng thái: Đang giao bằng drone (chặng drone: drone đang bay tới tủ nhận)"));
        assertTrue(text.contains("- Tủ nhận: Tủ Cổng B · ô số 2"));
        assertTrue(text.contains("- Bước tiếp theo: Chờ đội bay tiếp nhận và phóng drone"));
    }

    @Test
    void reportsSourcesThatCouldNotBeLoadedAndUnknownMentionedCodes() {
        Snapshot snapshot = new Snapshot(null, List.of(), false, List.of(), Map.of(), Map.of(),
                Set.of("ORD-20261001-NOPE"));

        String text = formatter.format(snapshot, NOW).content();

        assertTrue(text.contains("Không tải được danh sách đơn bạn đã tạo lúc này; chưa có đơn nào người khác gửi cho bạn."));
        assertTrue(text.contains("Không thấy đơn ORD-20261001-NOPE trong các đơn của người hỏi"));

        String noIncoming = formatter.format(new Snapshot(List.of(), null, false, List.of(), Map.of(), Map.of(), Set.of()),
                NOW).content();
        assertTrue(noIncoming.contains("Bạn chưa tạo đơn nào; không tải được danh sách hàng người khác gửi cho bạn lúc này."));
    }

    @Test
    void selectionKeepsActiveAndMentionedOrdersThenFillsWithTheNewest() {
        List<MyOrder> mine = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            mine.add(mine(100L + i, "ORD-202610" + String.format("%02d", 1 + (i % 28)) + "-C" + i, "SEND", "COMPLETED",
                    "PAID", null, LocalDateTime.parse("2026-10-12T00:00:00").minusDays(i)));
        }
        MyOrder oldActive = mine(200L, "ORD-20260901-ACT", "RENTAL", "STORING", "PAID", null,
                LocalDateTime.parse("2026-09-01T00:00:00"));
        mine.add(oldActive);
        IncomingOrder sentToMe = incoming(300L, "ORD-20261013-IN", "STORING", LocalDateTime.parse("2026-10-13T00:00:00"));
        // Trùng id với đơn tự tạo (tự gửi cho mình) ⇒ chỉ giữ một bản.
        IncomingOrder duplicate = incoming(100L, mine.get(0).orderCode(), "COMPLETED", mine.get(0).createdAt());
        String oldest = mine.get(19).orderCode();

        List<Entry> selected = OrderDocumentFormatter.select(mine, List.of(sentToMe, duplicate), Set.of(oldest));

        assertEquals(OrderDocumentFormatter.MAX_ORDERS, selected.size());
        assertTrue(selected.stream().anyMatch(entry -> entry.mine() == oldActive), "active order always included");
        assertTrue(selected.stream().anyMatch(entry -> oldest.equals(entry.orderCode())), "mentioned order included");
        assertEquals(1, selected.stream().filter(entry -> Long.valueOf(100L).equals(entry.id())).count());
        assertNotNull(selected.stream().filter(entry -> Long.valueOf(100L).equals(entry.id())).findFirst().orElseThrow().mine());
        assertEquals(sentToMe, selected.get(0).incoming());
        for (int i = 1; i < selected.size(); i++) {
            assertFalse(selected.get(i).createdAt().isAfter(selected.get(i - 1).createdAt()), "newest first");
        }
    }

    @Test
    void formatsMoneyDurationsAndPhones() {
        assertEquals("1.250.000đ", OrderDocumentFormatter.money(new BigDecimal("1250000.40")));
        assertEquals("0đ", OrderDocumentFormatter.money(null));
        assertEquals("2 giờ 15 phút", OrderDocumentFormatter.duration(Duration.ofMinutes(135)));
        assertEquals("40 phút", OrderDocumentFormatter.duration(Duration.ofMinutes(40)));
        assertEquals("2 ngày", OrderDocumentFormatter.duration(Duration.ofHours(48).plusMinutes(5)));
        assertEquals("dưới 1 phút", OrderDocumentFormatter.duration(Duration.ofSeconds(20)));
        assertEquals("789", OrderDocumentFormatter.maskedPhone("+84 912 345 789"));
        assertNull(OrderDocumentFormatter.maskedPhone("123"));
        assertNull(OrderDocumentFormatter.maskedPhone(null));
    }

    private ContextDocument format(List<MyOrder> mine, List<IncomingOrder> incoming, Map<Long, LockerInfo> lockers,
                                   Map<Long, Integer> boxes, Set<String> mentioned) {
        List<Entry> selected = OrderDocumentFormatter.select(mine, incoming, mentioned);
        return formatter.format(new Snapshot(mine, incoming, false, selected, lockers, boxes, mentioned), NOW);
    }

    private static MyOrder mine(Long id, String code, String type, String status, String payment,
                                LocalDateTime deadline, LocalDateTime created) {
        return new MyOrder(id, code, type, status, null, null, null, null, payment, new BigDecimal("15000"),
                new BigDecimal("15000"), BigDecimal.ZERO, null, false, deadline, null, created, null, null, null,
                null, null);
    }

    private static IncomingOrder incoming(Long id, String code, String status, LocalDateTime created) {
        return new IncomingOrder(id, code, "SEND", status, null, "Người gửi", null, null, null, null, false, false,
                "PAID", false, created, null);
    }
}
