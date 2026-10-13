package com.huynqb.laundrylocker.assistant.query;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/// Xử lý chữ tiếng Việt cho trợ lý: bỏ dấu để so khớp từ khoá không phân biệt có dấu/không dấu, và
/// nhận ra câu tiếng Việt gõ không dấu ("lam sao de gui hang") để thêm dấu trước khi tìm tài liệu.
public final class VietnameseText {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");

    /// Cụm từ chỉ tiếng Việt không dấu mới có; gặp một cụm là đủ.
    private static final List<String> PHRASES = List.of(
            "nhu the nao", "the nao", "lam sao", "lam the nao", "o dau", "bao nhieu", "khi nao", "bao gio",
            "duoc khong", "co the", "la gi", "cua toi", "cua minh", "tai sao", "vi sao", "gui hang", "nhan hang",
            "lay hang", "thue tu", "thue o", "thanh toan", "don hang", "mat khau", "tai khoan", "hoan tien",
            "gia han", "het han", "het gio", "ma pin", "o tu", "xin chao", "cam on", "co khong", "duoc ko");

    /// Từ tiếng Việt không dấu hầu như không trùng từ tiếng Anh.
    private static final Set<String> STRONG_WORDS = Set.of(
            "khong", "ko", "cua", "toi", "duoc", "nhu", "nao", "lam", "sao", "khi", "dau", "bao", "nhieu", "thue",
            "minh", "chua", "thi", "voi", "nhung", "mot", "ngay", "gio", "phut", "tien", "thanh", "toan", "vay",
            "nhe", "nua", "het", "giao", "khoan", "khau", "nhap", "xoa", "huy", "hoan", "muon", "phai", "nguoi",
            "thay", "kiem", "sua", "quen", "chuyen", "khoa", "cai", "nhat", "truoc", "biet", "hoi", "xin", "gium",
            "giup", "dang", "dien", "thoai", "duong", "trong", "ngoai", "cach", "huong", "lieu", "roi", "duoi",
            "tren", "nhan");

    /// Từ ngắn có cả trong tiếng Anh hoặc tên riêng: chỉ góp vào tỉ lệ, không đủ để kết luận.
    private static final Set<String> WEAK_WORDS = Set.of(
            "tu", "o", "la", "gi", "ban", "co", "de", "va", "cho", "nay", "ma", "cac", "em", "anh", "chi", "da",
            "se", "di", "den", "ve", "ra", "vao", "len", "hang", "don", "bi", "mo", "tra", "hong", "ket", "tai",
            "mat", "on", "phi", "dung", "sau", "can", "bay", "a", "oi", "ne", "nha", "no", "ai", "khac", "qua",
            "gui", "lay", "bang", "dan", "luc");

    private VietnameseText() {
    }

    /// Chữ thường, bỏ dấu (kể cả đ → d), mọi ký tự không phải chữ/số thành một khoảng trắng.
    /// "Đơn hàng CỦA tôi?" và "don hang cua toi" cho cùng kết quả "don hang cua toi".
    public static String fold(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT).replace('đ', 'd');
        String stripped = MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
        return NON_WORD.matcher(stripped).replaceAll(" ").trim();
    }

    /// Có chữ Latin mang dấu (á, ơ, ư, đ…) hoặc dấu tổ hợp rời.
    public static boolean hasDiacritics(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 'đ' || c == 'Đ' || (c >= '̀' && c <= 'ͯ')) {
                return true;
            }
            if (c > 0x7F && Character.isLetter(c)
                    && Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN) {
                return true;
            }
        }
        return false;
    }

    /// Câu tiếng Việt gõ không dấu: có chữ Latin, không có ký tự mang dấu, và có từ/cụm từ tiếng Việt
    /// thông dụng ở dạng không dấu. Câu tiếng Anh và câu đã có dấu trả false.
    public static boolean isUnaccentedVietnamese(String text) {
        if (text == null || text.isBlank() || hasDiacritics(text)) {
            return false;
        }
        String folded = fold(text);
        List<String> words = Arrays.stream(folded.split(" "))
                .filter(word -> !word.isEmpty() && word.chars().anyMatch(Character::isLetter))
                .toList();
        if (words.isEmpty()) {
            return false;
        }
        String padded = " " + folded + " ";
        for (String phrase : PHRASES) {
            if (padded.contains(" " + phrase + " ")) {
                return true;
            }
        }
        long strong = words.stream().filter(STRONG_WORDS::contains).count();
        long weak = words.stream().filter(WEAK_WORDS::contains).count();
        if (strong >= 2) {
            return true;
        }
        return strong == 1 && (strong + weak) * 2 >= words.size();
    }

    /// Hai câu chỉ khác nhau ở dấu (và dấu câu/khoảng trắng): dùng để chắc chắn bản "đã thêm dấu" mô
    /// hình trả về không đổi nghĩa, mã đơn hay con số. Chữ số phải giữ y nguyên; chữ cái cho lệch tối
    /// đa ~10% (sửa lỗi gõ).
    public static boolean sameTextIgnoringDiacritics(String original, String restored) {
        String a = fold(original).replace(" ", "");
        String b = fold(restored).replace(" ", "");
        if (a.isEmpty() || b.isEmpty() || !a.replaceAll("\\D", "").equals(b.replaceAll("\\D", ""))) {
            return false;
        }
        int allowed = Math.max(1, Math.max(a.length(), b.length()) / 10);
        if (Math.abs(a.length() - b.length()) > allowed) {
            return false;
        }
        return editDistance(a, b) <= allowed;
    }

    private static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
