package com.huynqb.laundrylocker.order.service;

import java.text.Normalizer;
import java.util.regex.Pattern;

/// Đổi chữ sang dạng gửi SMS được trong một tin GSM-7: tiếng Việt bỏ dấu, ký tự ngoài
/// ASCII in được bị bỏ.
///
/// Chỉ cần một ký tự Unicode là cả tin chuyển sang UCS-2 (70 ký tự/tin thay vì 160) —
/// tên tủ admin gõ có dấu ("Tủ Thư viện") hay dấu gạch dài "–" cũng đủ làm vậy.
final class SmsText {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_PRINTABLE_ASCII = Pattern.compile("[^\\x20-\\x7E]");
    private static final Pattern SPACES = Pattern.compile("\\s{2,}");

    private SmsText() {
    }

    /// `null` ⇒ chuỗi rỗng.
    static String ascii(String text) {
        if (text == null) return "";
        String plain = text.replace('đ', 'd').replace('Đ', 'D')
                .replace('–', '-').replace('—', '-')
                .replace('‘', '\'').replace('’', '\'')
                .replace('“', '"').replace('”', '"');
        plain = MARKS.matcher(Normalizer.normalize(plain, Normalizer.Form.NFD)).replaceAll("");
        plain = NON_PRINTABLE_ASCII.matcher(plain).replaceAll(" ");
        return SPACES.matcher(plain).replaceAll(" ").trim();
    }
}
