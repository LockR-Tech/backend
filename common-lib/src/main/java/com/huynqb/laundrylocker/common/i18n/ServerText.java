package com.huynqb.laundrylocker.common.i18n;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Dịch chữ server tự soạn (lỗi, tiêu đề/nội dung thông báo, ghi chú mốc đơn…) sang tiếng Anh theo
/// bảng `i18n/server-text-en.json`: khoá là câu tiếng Việt đúng như trong code, `{}` thay cho phần ghép
/// động (mã đơn, số tiền…); giá trị là câu tiếng Anh, `{}` lấy lần lượt, `{0}` `{1}` khi cần đổi thứ tự.
///
/// Thứ tự thử: khớp nguyên câu → khớp mẫu có `{}` → tách theo dòng/câu rồi dịch từng phần → thay các mảnh
/// câu ghép bằng `StringBuilder` (khoá bắt đầu/kết thúc bằng khoảng trắng). Không khớp gì thì giữ nguyên.
/// Phần ghép động có chữ tiếng Việt (nhãn trạng thái, lý do…) cũng được dịch tiếp.
@Slf4j
public final class ServerText {

    public static final String LANGUAGE_HEADER = "X-App-Language";

    private static final String CATALOG = "i18n/server-text-en.json";
    private static final Pattern VIETNAMESE = Pattern.compile(
            "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ"
                    + "ÀÁẠẢÃÂẦẤẬẨẪĂẰẮẶẲẴÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸĐ]");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d*)}");
    /// Đoạn chữ giữa hai thẻ HTML.
    private static final Pattern HTML_TEXT = Pattern.compile(">([^<>]+)<");
    /// Giờ in kiểu `HH:mm 'ngày' dd/MM/yyyy` nằm trong phần ghép động.
    private static final Pattern DATE_TIME = Pattern.compile("(\\d{1,2}:\\d{2}) ngày (\\d{1,2}/\\d{1,2}/\\d{4})");
    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?;:])\\s+|\\s+—\\s+|\\s+-\\s+|\\s*\\|\\s*");
    /// Mẫu chỉ có vài chữ cố định ("Tủ {}") dễ khớp nhầm tên do admin đặt ⇒ không dùng làm mẫu, trừ khi
    /// có từ hai chỗ ghép trở lên ("Mã {}: {}").
    private static final int MIN_TEMPLATE_LITERAL = 6;
    private static final int MIN_FRAGMENT = 12;
    private static final int CACHE_LIMIT = 10_000;
    /// Số lớp câu lồng trong phần ghép động được dịch tiếp (đoạn HTML đã bắt đầu ở lớp 1).
    private static final int MAX_DEPTH = 3;

    private static final ServerText INSTANCE = load();

    private final Map<String, String> exact;
    private final List<Template> templates;
    /// Mẫu ngắn chỉ dùng cho phần ghép động bên trong câu khác ("Kiện hàng {} đã vào tủ #3").
    private final List<Template> shortTemplates;
    private final List<Map.Entry<String, String>> fragments;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    ServerText(Map<String, String> catalog) {
        this.exact = new HashMap<>();
        this.templates = new ArrayList<>();
        this.shortTemplates = new ArrayList<>();
        this.fragments = new ArrayList<>();
        catalog.forEach((vi, en) -> {
            if (vi == null || en == null || en.isBlank()) {
                return;
            }
            if (!PLACEHOLDER.matcher(vi).find()) {
                exact.put(normalize(vi), en.strip());
                if (isFragment(vi) && vi.strip().length() >= MIN_FRAGMENT) {
                    fragments.add(Map.entry(vi.strip(), en.strip()));
                }
                return;
            }
            Template template = Template.of(vi, en);
            if (template.literalLength >= MIN_TEMPLATE_LITERAL
                    || (template.pattern.matcher("").groupCount() >= 2 && template.literalLength >= 3)) {
                templates.add(template);
            } else {
                shortTemplates.add(template);
            }
        });
        templates.sort(Comparator.comparingInt((Template t) -> t.literalLength).reversed());
        shortTemplates.sort(Comparator.comparingInt((Template t) -> t.literalLength).reversed());
        fragments.sort(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed());
    }

    /// Request có xin tiếng Anh không (`X-App-Language: en`, `en-US`…).
    public static boolean wantsEnglish(String languageHeader) {
        return languageHeader != null && languageHeader.trim().toLowerCase(Locale.ROOT).startsWith("en");
    }

    /// Bản tiếng Anh của câu server soạn; không có trong bảng thì trả lại nguyên câu.
    public static String toEnglish(String text) {
        return INSTANCE.translate(text);
    }

    /// Theo mã ngôn ngữ đã lưu (token FCM…): `en` ⇒ dịch, còn lại giữ tiếng Việt.
    public static String forLanguage(String language, String text) {
        return wantsEnglish(language) ? toEnglish(text) : text;
    }

    /// Thư HTML: dịch từng đoạn chữ nằm giữa hai thẻ (đã bỏ/khôi phục mã thực thể HTML) và đổi `lang="vi"`.
    /// Template thư chèn từng câu server soạn vào một thẻ riêng nên mỗi đoạn chữ là một câu trọn vẹn.
    public static String htmlToEnglish(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        Matcher m = HTML_TEXT.matcher(html);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String text = m.group(1);
            String translated = text;
            if (VIETNAMESE.matcher(text).find()) {
                // Mỗi ô của bảng chi tiết là một đoạn riêng ("Ô số 3") nên được dùng cả mẫu ngắn.
                translated = escapeHtml(INSTANCE.translate(unescapeHtml(text), 1));
            }
            m.appendReplacement(out, Matcher.quoteReplacement(">" + translated + "<"));
        }
        m.appendTail(out);
        return out.toString().replace("lang=\"vi\"", "lang=\"en\"");
    }

    /// SMS viết tiếng Việt không dấu (`"Lock.R: Ban co kien hang …"`) nên không qua được bước nhận diện tiếng
    /// Việt của [#toEnglish]: khớp thẳng với mẫu câu SMS trong bảng.
    public static String smsToEnglish(String sms) {
        if (sms == null || sms.isBlank()) {
            return sms;
        }
        String translated = INSTANCE.translateWhole(sms, MAX_DEPTH);
        return translated == null ? sms : translated.replace(", o so ", ", box no. ");
    }

    String translate(String text) {
        if (text == null || text.isEmpty() || !VIETNAMESE.matcher(text).find()) {
            return text;
        }
        String cached = cache.get(text);
        if (cached != null) {
            return cached;
        }
        String result = translate(text, 0);
        if (cache.size() >= CACHE_LIMIT) {
            cache.clear();
        }
        cache.put(text, result);
        return result;
    }

    private String translate(String original, int depth) {
        String text = DATE_TIME.matcher(original).replaceAll("$1 on $2");
        if (!VIETNAMESE.matcher(text).find()) {
            return text;
        }
        String whole = translateWhole(text, depth);
        if (whole != null) {
            return whole;
        }
        if (text.contains("\n")) {
            StringBuilder out = new StringBuilder();
            for (String line : text.split("\n", -1)) {
                if (!out.isEmpty()) {
                    out.append('\n');
                }
                out.append(translatePart(line, depth));
            }
            return out.toString();
        }
        return translatePart(text, depth);
    }

    /// Một dòng: nguyên câu, rồi tách câu, cuối cùng thay cụm cố định.
    private String translatePart(String text, int depth) {
        if (text.isBlank() || !VIETNAMESE.matcher(text).find()) {
            return text;
        }
        String whole = translateWhole(text, depth);
        if (whole != null) {
            return whole;
        }
        // "Nhãn: giá trị" (thư chữ trơn): nhãn có trong bảng thì dịch phần sau như một câu trọn.
        int colon = text.indexOf(": ");
        if (colon > 0) {
            String label = translateWhole(text.substring(0, colon + 1), depth);
            if (label != null) {
                return label + " " + translatePart(text.substring(colon + 2), depth);
            }
        }
        Matcher breaks = SENTENCE_BREAK.matcher(text);
        if (breaks.find()) {
            breaks.reset();
            StringBuilder out = new StringBuilder();
            int last = 0;
            boolean changed = false;
            while (breaks.find()) {
                String piece = text.substring(last, breaks.start());
                String translated = translateWhole(piece, depth);
                changed |= translated != null;
                out.append(translated != null ? translated : piece).append(breaks.group());
                last = breaks.end();
            }
            String tail = text.substring(last);
            String translatedTail = translateWhole(tail, depth);
            changed |= translatedTail != null;
            out.append(translatedTail != null ? translatedTail : tail);
            if (changed) {
                return replaceFragments(out.toString());
            }
        }
        return replaceFragments(text);
    }

    private String translateWhole(String text, int depth) {
        String key = normalize(text);
        if (key.isEmpty()) {
            return null;
        }
        String hit = exact.get(key);
        if (hit == null && key.endsWith(":")) {
            // Nhãn trong thư chữ trơn ("Mã sự cố: DPI-7") tách ra còn "Mã sự cố:" — bảng chỉ có "Mã sự cố".
            String label = exact.get(key.substring(0, key.length() - 1).strip());
            hit = label == null ? null : label + ":";
        }
        if (hit != null) {
            return keepPadding(text, hit);
        }
        UnaryOperator<String> nested = depth < MAX_DEPTH ? value -> translate(value, depth + 1) : value -> value;
        String translated = applyFirst(templates, text, key, nested);
        if (translated == null && depth > 0) {
            translated = applyFirst(shortTemplates, text, key, nested);
        }
        return translated;
    }

    /// Khớp câu đã cắt khoảng trắng hai đầu, rồi câu gốc — mẫu kết thúc bằng `". {}"` mà phần ghép cuối rỗng
    /// chỉ khớp khi còn khoảng trắng.
    private static String applyFirst(List<Template> candidates, String text, String key, UnaryOperator<String> nested) {
        for (Template template : candidates) {
            String translated = template.apply(key, nested);
            if (translated != null) {
                return keepPadding(text, translated);
            }
            if (!key.equals(text)) {
                translated = template.apply(text, nested);
                if (translated != null) {
                    return translated;
                }
            }
        }
        return null;
    }

    private String replaceFragments(String text) {
        if (!VIETNAMESE.matcher(text).find()) {
            return text;
        }
        String out = text;
        for (Map.Entry<String, String> fragment : fragments) {
            if (out.contains(fragment.getKey())) {
                out = out.replace(fragment.getKey(), fragment.getValue());
            }
        }
        return out;
    }

    /// Mảnh của câu ghép (`.append(" Vị trí báo rơi: ")`) — bắt đầu/kết thúc bằng khoảng trắng hoặc dấu câu.
    /// Chỉ các mảnh này được thay giữa câu, để chữ người dùng tự gõ (địa chỉ, phản hồi) không bị dịch lỗ chỗ.
    private static boolean isFragment(String vi) {
        char first = vi.charAt(0);
        char last = vi.charAt(vi.length() - 1);
        return Character.isWhitespace(first) || Character.isWhitespace(last)
                || ",.;:".indexOf(first) >= 0;
    }

    private static String unescapeHtml(String text) {
        return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&amp;", "&");
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String keepPadding(String original, String translated) {
        int start = 0;
        while (start < original.length() && Character.isWhitespace(original.charAt(start))) {
            start++;
        }
        int end = original.length();
        while (end > start && Character.isWhitespace(original.charAt(end - 1))) {
            end--;
        }
        return original.substring(0, start) + translated + original.substring(end);
    }

    private static String normalize(String text) {
        return text.strip();
    }

    private static ServerText load() {
        try (InputStream in = ServerText.class.getClassLoader().getResourceAsStream(CATALOG)) {
            if (in == null) {
                log.warn("Thiếu {} — không dịch được chữ server sang tiếng Anh", CATALOG);
                return new ServerText(Map.of());
            }
            Map<String, String> catalog = new ObjectMapper().readValue(in, new TypeReference<LinkedHashMap<String, String>>() { });
            return new ServerText(catalog);
        } catch (IOException ex) {
            log.warn("Không đọc được {}: {}", CATALOG, ex.getMessage());
            return new ServerText(Map.of());
        }
    }

    /// Một mẫu có `{}`: regex khớp nguyên câu tiếng Việt và câu tiếng Anh để điền lại.
    private record Template(Pattern pattern, String english, int literalLength) {

        static Template of(String vi, String en) {
            String source = normalize(vi);
            StringBuilder regex = new StringBuilder("^");
            Matcher m = PLACEHOLDER.matcher(source);
            int last = 0;
            int literal = 0;
            // Mẫu một dòng không được khớp xuyên dòng ("Đơn {}: {}" nuốt cả hai dòng của một đoạn thư).
            String group = source.contains("\n") ? "(.*?)" : "([^\\n]*?)";
            while (m.find()) {
                String text = source.substring(last, m.start());
                literal += text.length();
                regex.append(Pattern.quote(text)).append(group);
                last = m.end();
            }
            String tail = source.substring(last);
            literal += tail.length();
            regex.append(Pattern.quote(tail)).append('$');
            return new Template(Pattern.compile(regex.toString(), Pattern.DOTALL), normalize(en), literal);
        }

        String apply(String text, UnaryOperator<String> nested) {
            Matcher m = pattern.matcher(text);
            if (!m.matches()) {
                return null;
            }
            // Mẫu ít chữ cố định ("Mã {}: {}" của mã giảm giá): phần ghép trước phần cuối phải là một từ, để
            // "Mã mở tủ: 123456" không bị hiểu thành mã "mở tủ".
            if (literalLength < MIN_TEMPLATE_LITERAL) {
                for (int i = 1; i < m.groupCount(); i++) {
                    if (m.group(i).chars().anyMatch(Character::isWhitespace)) {
                        return null;
                    }
                }
            }
            List<String> values = new ArrayList<>(m.groupCount());
            for (int i = 1; i <= m.groupCount(); i++) {
                values.add(nested.apply(m.group(i)));
            }
            StringBuilder out = new StringBuilder();
            Matcher p = PLACEHOLDER.matcher(english);
            int next = 0;
            while (p.find()) {
                int index = p.group(1).isEmpty() ? next++ : Integer.parseInt(p.group(1));
                p.appendReplacement(out, Matcher.quoteReplacement(index < values.size() ? values.get(index) : ""));
            }
            p.appendTail(out);
            return out.toString();
        }
    }
}
