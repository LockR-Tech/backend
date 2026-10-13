package com.huynqb.laundrylocker.assistant.query;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Câu hỏi có nhắc tới đơn hàng/ô tủ của chính người hỏi không ("Đơn hôm qua của tôi đã có người lấy
/// chưa?", "tu toi thue con bao lau het gio"). So khớp trên chữ đã bỏ dấu nên có dấu hay không dấu như
/// nhau. Câu hướng dẫn chung ("Làm sao để gửi hàng?") cố ý không tính — tài liệu hướng dẫn đã trả lời.
public final class OrderIntent {

    /// Mã đơn order-service: `ORD-yyyyMMdd-XXXXXX`.
    private static final Pattern ORDER_CODE =
            Pattern.compile("\\bORD-\\d{8}-[A-Z0-9]{1,8}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENTENCE_END = Pattern.compile("[.?!;\\n]+");

    private static final String ME = "(?:toi|minh|em|tui|tao)";
    /// Danh từ chắc chắn là đơn/hàng.
    private static final String ORDER_NOUN = "(?:don|don hang|hang|goi hang|kien hang|kien|buu kien|buu pham"
            + "|luot thue|ma pin|ma qr|ma lay hang|ma mo tu|ma mo o)";
    /// Bỏ dấu thì trùng từ khác (ô/ở, tủ/từ, đồ/đó) ⇒ chỉ cho cách "của tôi" rất gần.
    private static final String SHORT_NOUN = "(?:tu|o|o tu|do)";

    /// Khớp trên cả câu đã bỏ dấu; một mẫu khớp là đủ.
    private static final List<Pattern> STRONG = List.of(
            // Nhắc mã đơn (kể cả gõ thiếu).
            Pattern.compile("\\bord ?\\d{6,}"),
            // "đơn (gửi hàng hôm qua) của tôi", "hàng của em", "tủ của mình"
            Pattern.compile("\\b" + ORDER_NOUN + "(?: \\w+){0,5} cua " + ME + "\\b"),
            Pattern.compile("\\b" + SHORT_NOUN + "(?: \\w+){0,2} cua " + ME + "\\b"),
            // "tủ tôi thuê", "đơn mình đặt", "hàng tôi gửi"
            Pattern.compile("\\b(?:don|tu|o|hang|kien|do) " + ME
                    + " (?:da |vua |moi |dang )?(?:thue|dat|gui|tao|nhan|mua)\\b"),
            // "tôi đã gửi", "mình vừa đặt", "em mới thuê"
            Pattern.compile("\\b" + ME + "(?: \\w+)? (?:da|vua|moi|dang) (?:dat|gui|thue|tao|book|mua|nhan duoc)\\b"),
            // Hàng người khác gửi cho tôi.
            Pattern.compile("\\b(?:ai|nguoi nao)(?: \\w+){0,3} gui(?: \\w+){0,2} (?:cho|toi|den) " + ME + "\\b"),
            Pattern.compile("\\bco (?:hang|don|goi|kien|buu pham)(?: \\w+){0,4} (?:cho|toi|den|ve) " + ME + "\\b"),
            Pattern.compile("\\bhang (?:gui )?(?:cho|den|toi|ve) " + ME + "\\b"),
            Pattern.compile("\\bhang (?:cho nhan|dang den|sap den)\\b"),
            // Hỏi tình trạng / thời hạn.
            Pattern.compile("\\b(?:giao|bay|drone|hang|don)(?: \\w+){0,2} (?:toi|den) dau(?: roi)?\\b"),
            Pattern.compile("\\bcon bao (?:lau|nhieu (?:gio|phut|ngay|tieng))\\b"),
            Pattern.compile("\\btrang thai (?:cua )?(?:don|hang|tu|o|kien)\\b"),
            Pattern.compile("\\b(?:don|hang|kien)(?: \\w+)? (?:hom qua|hom nay|hom kia|tuan truoc|thang truoc|vua roi"
                    + "|gan nhat|gan day|moi nhat|cuoi cung|vua gui|vua dat|vua thue|dang thue|dang gui)\\b"),
            Pattern.compile("\\b(?:bao nhieu|tat ca|cac|nhung) don (?:cua|ma|minh|toi|em|dang)\\b"),
            Pattern.compile("\\bbao nhieu don\\b"),
            // Tiếng Anh.
            Pattern.compile("\\bmy (?:orders?|lockers?|parcels?|packages?|rentals?|deliver(?:y|ies)|pin|codes?)\\b"),
            Pattern.compile("\\b(?:sent|delivered) to me\\b"));

    /// Câu hỏi tình trạng kết thúc bằng "chưa" ("đã có người lấy chưa?", "drone tới chưa?"); khớp ở
    /// cuối từng câu để "Người nhận chưa có tài khoản thì sao?" không bị tính.
    private static final List<Pattern> STATUS_AT_END = List.of(
            Pattern.compile("\\b(?:ai|nguoi)(?: \\w+){0,2} (?:lay|nhan|mo)(?: \\w+){0,2} chua(?: \\w+)?$"),
            Pattern.compile("\\b(?:giao|lay|nhan|thanh toan|tra tien|hoan tien|xong|bay|toi noi|den noi)"
                    + "(?: hang| tien)? chua(?: \\w+)?$"),
            Pattern.compile("\\b(?:hang|don|drone|kien)(?: \\w+){0,2} (?:den|toi)(?: noi)? chua(?: \\w+)?$"));

    /// Chủ đề đơn hàng — chỉ tính khi đi cùng đại từ ngôi thứ nhất và không phải câu hỏi cách làm.
    private static final Pattern TOPIC = Pattern.compile(
            "\\b(?:don|don hang|nhan hang|lay hang|het han|het gio|gia han|thanh toan|hoan tien|drone|giao hang"
                    + "|trang thai|o tu|phi qua han|qua han|ma pin|ma qr|ma lay hang|ma mo)\\b");
    private static final Pattern FIRST_PERSON = Pattern.compile("\\b" + ME + "\\b");
    private static final Pattern HOW_TO = Pattern.compile(
            "\\b(?:lam sao|lam the nao|the nao de|nhu the nao|cach|huong dan|quy trinh|duoc khong|co duoc"
                    + "|bao nhieu tien|mat phi|la gi|nghia la)\\b");
    /// "đơn giản", "đơn vị", "dọn dẹp"… cũng bỏ dấu thành "don".
    private static final Pattern NOT_ORDER = Pattern.compile("\\bdon (?:gian|vi|le|dep|sach|dien)\\b");

    private OrderIntent() {
    }

    public static boolean isOrderQuestion(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (ORDER_CODE.matcher(text).find()) {
            return true;
        }
        String folded = clean(text);
        for (Pattern pattern : STRONG) {
            if (pattern.matcher(folded).find()) {
                return true;
            }
        }
        for (String sentence : SENTENCE_END.split(text)) {
            String foldedSentence = clean(sentence);
            for (Pattern pattern : STATUS_AT_END) {
                if (pattern.matcher(foldedSentence).find()) {
                    return true;
                }
            }
        }
        return TOPIC.matcher(folded).find()
                && FIRST_PERSON.matcher(folded).find()
                && !HOW_TO.matcher(folded).find();
    }

    /// Mã đơn nhắc trong câu, viết hoa, theo thứ tự xuất hiện.
    public static Set<String> orderCodes(String text) {
        Set<String> codes = new LinkedHashSet<>();
        if (text == null) {
            return codes;
        }
        Matcher matcher = ORDER_CODE.matcher(text);
        while (matcher.find()) {
            codes.add(matcher.group().toUpperCase(Locale.ROOT));
        }
        return codes;
    }

    private static String clean(String text) {
        return NOT_ORDER.matcher(VietnameseText.fold(text)).replaceAll(" ");
    }
}
