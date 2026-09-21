package com.huynqb.laundrylocker.assistant.settings;

import com.huynqb.laundrylocker.common.settings.SettingDefinition;
import com.huynqb.laundrylocker.common.settings.SettingsCatalog;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.huynqb.laundrylocker.common.settings.SettingDefinition.bool;
import static com.huynqb.laundrylocker.common.settings.SettingDefinition.integer;

/// Quy tắc của trợ lý admin chỉnh được (ADR-0005) — /api/admin/settings/assistant.
@Component
public class AssistantSettingsCatalog implements SettingsCatalog {

    public static final String ENABLED = "app.assistant.enabled";
    public static final String MIN_SCORE_PERCENT = "app.assistant.min-score-percent";
    public static final String TOP_K = "app.assistant.top-k";
    public static final String QUESTIONS_PER_HOUR = "app.assistant.questions-per-hour";
    public static final String HISTORY_TURNS = "app.assistant.history-turns";

    private static final String ASSISTANT = "Trợ lý hỏi đáp";

    @Override
    public List<SettingDefinition> definitions() {
        return List.of(
                bool(ENABLED, ASSISTANT, "Bật trợ lý hỏi đáp",
                        "Tắt thì app ẩn màn hình trợ lý và API hỏi đáp trả lỗi.", true).asPublic(),
                integer(MIN_SCORE_PERCENT, ASSISTANT, "Ngưỡng liên quan tối thiểu",
                        "Đoạn tài liệu giống câu hỏi dưới ngưỡng này bị bỏ qua; không đoạn nào đạt thì trợ lý trả "
                                + "lời \"tài liệu chưa đề cập\" mà không gọi mô hình. Tinh chỉnh bằng bộ đánh giá.",
                        35, 0, 100, "%"),
                integer(TOP_K, ASSISTANT, "Số đoạn tài liệu gửi kèm mỗi câu hỏi",
                        "Nhiều đoạn hơn thì trả lời đầy đủ hơn nhưng tốn chi phí hơn.", 5, 1, 10, "đoạn"),
                integer(QUESTIONS_PER_HOUR, ASSISTANT, "Số câu hỏi tối đa mỗi giờ của một người",
                        "Khống chế chi phí gọi mô hình.", 30, 1, 1000, "câu"),
                integer(HISTORY_TURNS, ASSISTANT, "Số lượt hội thoại trước gửi kèm",
                        "Giúp hiểu câu hỏi nối tiếp (\"còn phí thì sao?\"). 0 = mỗi câu độc lập.",
                        3, 0, 10, "lượt"));
    }
}
