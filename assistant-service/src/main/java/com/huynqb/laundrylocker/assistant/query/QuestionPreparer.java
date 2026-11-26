package com.huynqb.laundrylocker.assistant.query;

import com.huynqb.laundrylocker.assistant.provider.DiacriticsRestorer;
import com.huynqb.laundrylocker.assistant.provider.QueryTranslator;
import com.huynqb.laundrylocker.assistant.settings.AssistantRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/// Chuẩn bị câu hỏi trước khi tìm tài liệu — dùng chung cho hỏi đáp và bộ đánh giá để kết quả đánh
/// giá phản ánh đúng production. Câu tiếng Việt gõ không dấu được thêm dấu (nếu admin bật); câu viết bằng
/// ngôn ngữ khác (tiếng Anh) được dịch sang tiếng Việt CHỈ để tìm trong kho tài liệu tiếng Việt (F3-G15) —
/// mô hình vẫn nhận câu gốc. Mọi lỗi hoặc kết quả lệch nghĩa ⇒ giữ nguyên câu gốc.
@Slf4j
@Component
public class QuestionPreparer {

    /// `text` là câu dùng để nhận diện ý định và gửi cho mô hình; `searchText` là câu dùng để tìm tài liệu
    /// (bản dịch tiếng Việt khi người hỏi viết tiếng Anh, còn lại bằng `text`); `original` là câu người dùng
    /// gõ (lưu vào hội thoại).
    public record PreparedQuestion(String original, String text, boolean restored, String searchText) {

        public PreparedQuestion(String original, String text, boolean restored) {
            this(original, text, restored, text);
        }

        static PreparedQuestion unchanged(String question) {
            return new PreparedQuestion(question, question, false);
        }

        public boolean translated() {
            return !searchText.equals(text);
        }
    }

    /// Một từ chữ cái Latin dài từ 3 ký tự (có thể kèm dấu câu cuối) — mã đơn "ORD-…" không tính là từ.
    private static final Pattern WORD = Pattern.compile("(?i)[a-z]{3,}[?.!,:;]*");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final DiacriticsRestorer restorer;
    private final AssistantRules rules;
    private final QueryTranslator translator;

    @Autowired
    public QuestionPreparer(DiacriticsRestorer restorer, AssistantRules rules, QueryTranslator translator) {
        this.restorer = restorer;
        this.rules = rules;
        this.translator = translator;
    }

    /// Không dịch câu ngôn ngữ khác (bộ đánh giá, test cũ).
    public QuestionPreparer(DiacriticsRestorer restorer, AssistantRules rules) {
        this(restorer, rules, null);
    }

    public PreparedQuestion prepare(String question) {
        if (looksForeign(question)) {
            return translateForSearch(question);
        }
        if (!rules.restoreDiacritics() || !restorer.configured() || !VietnameseText.isUnaccentedVietnamese(question)) {
            return PreparedQuestion.unchanged(question);
        }
        String restored;
        try {
            restored = restorer.restore(question);
        } catch (RuntimeException ex) {
            log.warn("Diacritics restoration failed, using the original question: {}", ex.getMessage());
            return PreparedQuestion.unchanged(question);
        }
        if (restored == null || restored.isBlank() || restored.equals(question)
                || !VietnameseText.sameTextIgnoringDiacritics(question, restored)) {
            log.info("Diacritics restoration result discarded (empty or changed the wording)");
            return PreparedQuestion.unchanged(question);
        }
        return new PreparedQuestion(question, restored.trim(), true);
    }

    /// Câu không dấu tiếng Việt nào và không phải tiếng Việt gõ không dấu ⇒ ngôn ngữ khác (thường là tiếng Anh).
    static boolean looksForeign(String question) {
        return question != null
                && !VietnameseText.hasDiacritics(question)
                && !VietnameseText.isUnaccentedVietnamese(question)
                && SPACES.splitAsStream(question.trim()).filter(word -> WORD.matcher(word).matches()).count() >= 2;
    }

    private PreparedQuestion translateForSearch(String question) {
        if (translator == null || !translator.configured()) {
            return PreparedQuestion.unchanged(question);
        }
        String vietnamese;
        try {
            vietnamese = translator.toVietnamese(question);
        } catch (RuntimeException ex) {
            log.warn("Question translation failed, searching with the original question: {}", ex.getMessage());
            return PreparedQuestion.unchanged(question);
        }
        if (vietnamese == null || vietnamese.isBlank() || !VietnameseText.hasDiacritics(vietnamese)) {
            log.info("Question translation result discarded (empty or not Vietnamese)");
            return PreparedQuestion.unchanged(question);
        }
        return new PreparedQuestion(question, question, false, vietnamese.trim());
    }
}
