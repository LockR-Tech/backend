package com.huynqb.laundrylocker.common.i18n;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerTextTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d*}");

    /// Nhật ký bộ điều khiển tủ, lỗi phần cứng Pi và mô tả phiếu kiểm tra drone hiện ở trang chi tiết tủ /
    /// bảo trì của web admin tiếng Anh (F3-G14), kể cả dòng cũ thiếu "· lúc …".
    @Test
    void lockerControllerLogsAndIncidentTicketsAreTranslated() {
        assertEquals("Controller disconnected (unplugged / offline) · GPIO · last seen at 09:08:46 13/11",
                ServerText.toEnglish("Bộ điều khiển mất kết nối (Rút nguồn điện / Ngoại tuyến) · GPIO · thấy lần cuối lúc 09:08:46 13/11"));
        assertEquals("Controller connected (powered on / started) · GPIO · 7 hardware boxes · firmware v1.0.0",
                ServerText.toEnglish("Bộ điều khiển kết nối thành công (Cấp nguồn điện / Khởi động) · GPIO · 7 ô phần cứng · firmware v1.0.0"));
        assertEquals("Controller reconnected (power restored / signal back) · GPIO · 7 hardware boxes · firmware v1.0.0 · at 10:00:00 26/11",
                ServerText.toEnglish("Bộ điều khiển kết nối lại thành công (Cấp điện / Bắt được tín hiệu) · GPIO · 7 ô phần cứng · firmware v1.0.0 · lúc 10:00:00 26/11"));
        assertEquals("The relay ran but the sensor still sees the door closed (door jammed or sensor not wired)",
                ServerText.toEnglish("Relay chạy nhưng cảm biến vẫn thấy cửa đóng (kẹt cửa hoặc cảm biến chưa nối)"));
        String ticket = ServerText.toEnglish("Kiểm tra hook/gripper, servo/actuator, dây điện và cơ cấu giữ hàng. "
                + "Sự cố DPI-192-1, đơn ORD-20261124-N2DH35. Vị trí báo rơi: 10.8, 106.7");
        assertTrue(ticket.startsWith("Check the hook/gripper, servo/actuator, wiring and the parcel holding mechanism. "
                + "Incident DPI-192-1, order ORD-20261124-N2DH35."), ticket);
    }

    @Test
    void exactSentence() {
        assertEquals("Payment successful", ServerText.toEnglish("Thanh toán thành công"));
        assertEquals("Insufficient wallet balance", ServerText.toEnglish("Số dư ví không đủ"));
    }

    @Test
    void templateFillsDynamicParts() {
        assertEquals("Order ORD-20261102-AB12CD was created successfully",
                ServerText.toEnglish("Đơn hàng ORD-20261102-AB12CD đã được tạo thành công"));
        assertEquals("The payment of 50.000đ for order #12 was unsuccessful. Please try again or choose another method.",
                ServerText.toEnglish("Thanh toán 50.000đ cho đơn #12 chưa thành công. Vui lòng thử lại hoặc chọn phương thức khác."));
    }

    @Test
    void droneTechnicianTextsHaveEnglish() {
        // Lý do lỗi drone / nhật ký bảo trì / ghi chú kết thúc nhiệm vụ khi báo rơi kiện.
        assertEquals("Parcel drop incident DPI-20261102-0007",
                ServerText.toEnglish("Sự cố rơi kiện DPI-20261102-0007"));
        // Câu "Sự cố rơi kiện drone" (tiêu đề thông báo admin) vẫn khớp nguyên câu.
        assertEquals("Drone parcel drop incident", ServerText.toEnglish("Sự cố rơi kiện drone"));
        // Ghi chú app KTV drone gửi khi nghiệm thu / cập nhật ảnh tiến độ.
        assertEquals("[INSPECTION PASSED] Fixed and checked that the drone works normally.",
                ServerText.toEnglish("[NGHIỆM THU THÀNH CÔNG] Đã khắc phục và kiểm tra Drone hoạt động bình thường."));
        assertEquals("Drone repair progress photos updated",
                ServerText.toEnglish("Cập nhật ảnh tiến độ sửa chữa Drone"));
    }

    @Test
    void vietnameseInsideDynamicPartIsTranslatedToo() {
        // "Mã {}: {}": lý do bên trong cũng là câu server soạn.
        assertEquals("Code SALE10: is invalid or has expired",
                ServerText.toEnglish("Mã SALE10: không hợp lệ hoặc đã hết hạn"));
        // Mẫu ngắn "tủ #{}" chỉ dùng cho phần ghép bên trong câu khác.
        assertEquals("Parcel ORD-1 is in locker #3. Box code: 123456. ",
                ServerText.toEnglish("Kiện hàng ORD-1 đã vào tủ #3. Mã mở ô: 123456. "));
    }

    @Test
    void composedDroneReasonsTranslateEveryPart() {
        // Thông báo "Đơn drone đang gặp sự cố": lý do ghép nhiều câu bằng " — " và ": " (F3-G14).
        assertEquals("Order ORD-1: Unsafe weather on the flight route — The weather does not allow flying: rain or thunderstorms"
                        + " on the route in the next 30 minutes.. Change the plan or confirm the delivery as not completed.",
                ServerText.toEnglish("Đơn ORD-1: Thời tiết tuyến bay không an toàn — Thời tiết không cho phép bay: có mưa hoặc dông"
                        + " trên tuyến trong 30 phút tới.. Đổi phương án hoặc xác nhận giao không hoàn tất."));
        assertEquals("Order ORD-1: The flight station is paused — Receiving locker has paused flights (Weather at the station):"
                        + " Automatic from the forecast: The weather does not allow flying: wind 7 km/h on the route in the next"
                        + " 30 minutes.. Change the plan or confirm the delivery as not completed.",
                ServerText.toEnglish("Đơn ORD-1: Trạm bay đang tạm dừng — Tủ nhận đang tạm dừng bay (Thời tiết tại trạm):"
                        + " Tự động theo dự báo: Thời tiết không cho phép bay: gió 7 km/h trên tuyến trong 30 phút tới.."
                        + " Đổi phương án hoặc xác nhận giao không hoàn tất."));
        assertEquals("Order ORD-1: The landing pad at the receiving locker is not ready — The sending locker has no usable drone"
                        + " left (ticket #57). Change the plan or confirm the delivery as not completed.",
                ServerText.toEnglish("Đơn ORD-1: Bãi đáp ở tủ nhận không sẵn sàng — Tủ gửi không còn drone nào dùng được"
                        + " (phiếu #57). Đổi phương án hoặc xác nhận giao không hoàn tất."));
        // Nhắc lịch bảo trì: "tại drone LR-DRN-09 sắp tới hạn" không bị cắt thành nơi "drone" + "LR-DRN-09 sắp tới hạn".
        assertEquals("Schedule \"Bảo dưỡng\" at drone LR-DRN-09 is coming due for inspection.",
                ServerText.toEnglish("Lịch \"Bảo dưỡng\" tại drone LR-DRN-09 sắp tới hạn kiểm tra."));
        assertEquals("Schedule \"Vệ sinh\" at locker Tủ A is due for inspection.",
                ServerText.toEnglish("Lịch \"Vệ sinh\" tại tủ Tủ A đã tới hạn kiểm tra."));
    }

    @Test
    void reorderedPlaceholdersKeepTheirValues() {
        // Câu tiếng Anh đổi thứ tự chỗ ghép bằng {1} {0}: số sao và mã phiếu không bị tráo ("RPT-5 53★").
        assertTrue(ServerText.toEnglish("Khách hàng đã đánh giá 5★ cho phiếu RPT-53 (tủ Tủ A): \"Tốt\"")
                .startsWith("The customer rated ticket RPT-53 5★ (locker Tủ A)"));
    }

    @Test
    void everyCatalogSentenceTranslatesWithSampleValues() throws Exception {
        Pattern vietnamese = Pattern.compile("[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹ"
                + "ÀÁẠẢÃÂẦẤẬẨẪĂẰẮẶẲẴÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸĐ]");
        List<String> untranslated = new ArrayList<>();
        catalog().keySet().forEach(vi -> {
            String sample = PLACEHOLDER.matcher(vi).replaceAll("X7");
            if (vietnamese.matcher(ServerText.toEnglish(sample)).find()) {
                untranslated.add(vi);
            }
        });
        // Còn lại chỉ là mẫu ngắn một chỗ ghép, dùng khi nằm trong câu khác.
        untranslated.forEach(vi -> {
            assertEquals(1, count(vi), vi);
            assertTrue(PLACEHOLDER.matcher(vi).replaceAll("").strip().length() < 6, vi);
        });
    }

    @Test
    void builderFragmentsAreReplacedInsideLongerText() {
        String vi = "Kiểm tra hook/gripper, servo/actuator, dây điện và cơ cấu giữ hàng. Incident DPI-7, đơn ORD-1. "
                + "Gió mạnh Vị trí báo rơi: 10.1, 106.2. Ảnh camera được lưu trong hồ sơ incident.";
        String en = ServerText.toEnglish(vi);
        assertTrue(en.startsWith("Check the hook/gripper"), en);
        assertTrue(en.contains("Reported drop location: 10.1, 106.2"), en);
        assertTrue(en.endsWith("The camera image is saved in the incident record."), en);
    }

    @Test
    void plainTextEmailTranslatesLineByLineButKeepsWhatTheCustomerTyped() {
        // Dựng như OrderService.receiverEmailBody: lời nhắn do người gửi gõ phải giữ nguyên.
        String vi = "Xin chào Trần An,\n\n"
                + "Nguyễn Bình đã gửi cho bạn một kiện hàng trong tủ Lock.R.\n\n"
                + "Mã đơn: ORD-1\n"
                + "Người gửi: Nguyễn Bình\n"
                + "Nơi lấy: Tủ Thư viện (Toà Alpha), ô số 4\n"
                + "Mã mở tủ: 123456\n"
                + "Hạn lấy hàng: 14:30 03/11/2026\n"
                + "Lời nhắn của người gửi: Nhớ lấy trước 5 giờ chiều nhé\n"
                + "\nNhập mã này trên màn hình tủ để mở ô và lấy hàng. Không chia sẻ mã cho người khác.\n";
        assertEquals("Hello Trần An,\n\n"
                + "Nguyễn Bình has sent you a parcel in a Lock.R locker.\n\n"
                + "Order code: ORD-1\n"
                + "Sender: Nguyễn Bình\n"
                + "Pickup at: Tủ Thư viện (Toà Alpha), box no. 4\n"
                + "Locker code: 123456\n"
                + "Pickup deadline: 14:30 03/11/2026\n"
                + "Message from the sender: Nhớ lấy trước 5 giờ chiều nhé\n"
                + "\nEnter this code on the locker screen to open the box and collect your parcel. "
                + "Do not share the code with anyone.\n", ServerText.toEnglish(vi));
    }

    @Test
    void htmlEmailTranslatesTextBetweenTags() {
        String html = "<html lang=\"vi\"><body><span>DRONE ĐANG GIAO</span>"
                + "<p>Xin chào bạn,</p><div>MÃ ĐƠN HÀNG <strong>ORD-1</strong></div>"
                + "<td>Người nhận</td><td>Trần &amp; An</td>"
                + "<p>Khi hàng vào tủ, bạn sẽ nhận email có mã mở ô và thời hạn lấy hàng.</p></body></html>";
        assertEquals("<html lang=\"en\"><body><span>DRONE ON THE WAY</span>"
                + "<p>Hello there,</p><div>ORDER CODE <strong>ORD-1</strong></div>"
                + "<td>Recipient</td><td>Trần &amp; An</td>"
                + "<p>When the parcel is in the locker, you will get an email with the box code and pickup deadline.</p>"
                + "</body></html>", ServerText.htmlToEnglish(html));
    }

    @Test
    void asciiSmsUsesSmsTemplates() {
        assertEquals("Lock.R: You have parcel ORD-1 at Tu Thu vien, box no. 4. Locker code: 123456. Pick up by: 14:30 03/11/2026.",
                ServerText.smsToEnglish(
                        "Lock.R: Ban co kien hang ORD-1 tai Tu Thu vien, o so 4. Ma mo tu: 123456. Han lay: 14:30 03/11/2026."));
        assertEquals("Hello", ServerText.smsToEnglish("Hello"));
    }

    @Test
    void multiLineTextTranslatesEachLine() {
        assertEquals("Order cancelled\nPaid", ServerText.toEnglish("Đơn đã hủy\nĐã thanh toán"));
    }

    @Test
    void dateTimeInsideSentence() {
        assertEquals("Your parcel will be stored for 24 hours, please collect it before 14:30 on 03/11/2026.",
                ServerText.toEnglish("Đơn hàng được lưu trữ 24 giờ, vui lòng lấy trước 14:30 ngày 03/11/2026."));
    }

    @Test
    void unknownAndNonVietnameseTextIsKept() {
        assertEquals("ORD-20261102-AB12CD", ServerText.toEnglish("ORD-20261102-AB12CD"));
        assertEquals("Nguyễn Văn An", ServerText.toEnglish("Nguyễn Văn An"));
        assertEquals("123 Lê Lợi, Quận 1", ServerText.toEnglish("123 Lê Lợi, Quận 1"));
        assertEquals("", ServerText.toEnglish(""));
        assertEquals(null, ServerText.toEnglish(null));
    }

    @Test
    void shortTemplatesDoNotRenameAdminData() {
        // "Tủ {}" chỉ có 3 ký tự cố định — tên tủ do admin đặt phải giữ nguyên.
        assertEquals("Tủ Bình Thạnh", ServerText.toEnglish("Tủ Bình Thạnh"));
    }

    @Test
    void languageHeader() {
        assertTrue(ServerText.wantsEnglish("en"));
        assertTrue(ServerText.wantsEnglish("en-US"));
        assertFalse(ServerText.wantsEnglish("vi"));
        assertFalse(ServerText.wantsEnglish(null));
        assertEquals("Thanh toán thành công", ServerText.forLanguage("vi", "Thanh toán thành công"));
        assertEquals("Payment successful", ServerText.forLanguage("en", "Thanh toán thành công"));
    }

    @Test
    void catalogPlaceholdersMatch() throws Exception {
        Map<String, String> catalog = catalog();
        assertTrue(catalog.size() > 1000);
        List<String> mismatched = new ArrayList<>();
        catalog.forEach((vi, en) -> {
            if (count(vi) != count(en) || en.isBlank()) {
                mismatched.add(vi);
            }
        });
        assertEquals(List.of(), mismatched);
    }

    @Test
    void responseTreeIsTranslatedInPlace() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree("""
                {"success":false,"code":"INSUFFICIENT","message":"Số dư ví không đủ",
                 "data":{"items":[{"title":"Thanh toán thành công","orderCode":"ORD-1"}],"count":1}}""");
        JsonNode out = EnglishResponseAdvice.translate(tree);
        assertEquals("Insufficient wallet balance", out.get("message").asText());
        assertEquals("Payment successful", out.at("/data/items/0/title").asText());
        assertEquals("ORD-1", out.at("/data/items/0/orderCode").asText());
        assertEquals("INSUFFICIENT", out.get("code").asText());
        assertEquals(1, out.at("/data/count").asInt());
    }

    private Map<String, String> catalog() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("i18n/server-text-en.json")) {
            return new ObjectMapper().readValue(in, new TypeReference<LinkedHashMap<String, String>>() { });
        }
    }

    private static int count(String text) {
        Matcher m = PLACEHOLDER.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
