package com.huynqb.laundrylocker.assistant.query;

import com.huynqb.laundrylocker.assistant.provider.DiacriticsRestorer;
import com.huynqb.laundrylocker.assistant.settings.AssistantRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// Chuẩn bị câu hỏi trước khi tìm tài liệu — dùng chung cho hỏi đáp và bộ đánh giá để kết quả đánh
/// giá phản ánh đúng production. Câu tiếng Việt gõ không dấu được thêm dấu (nếu admin bật); mọi lỗi
/// hoặc kết quả lệch nghĩa ⇒ giữ nguyên câu gốc.
@Slf4j
@Component
public class QuestionPreparer {

    /// `text` là câu dùng để tìm tài liệu, nhận diện ý định và gửi cho mô hình; `original` là câu
    /// người dùng gõ (lưu vào hội thoại).
    public record PreparedQuestion(String original, String text, boolean restored) {

        static PreparedQuestion unchanged(String question) {
            return new PreparedQuestion(question, question, false);
        }
    }

    private final DiacriticsRestorer restorer;
    private final AssistantRules rules;

    public QuestionPreparer(DiacriticsRestorer restorer, AssistantRules rules) {
        this.restorer = restorer;
        this.rules = rules;
    }

    public PreparedQuestion prepare(String question) {
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
}
