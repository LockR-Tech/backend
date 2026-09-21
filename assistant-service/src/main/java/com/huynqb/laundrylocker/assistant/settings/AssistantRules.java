package com.huynqb.laundrylocker.assistant.settings;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.huynqb.laundrylocker.assistant.settings.AssistantSettingsCatalog.*;

/// Đọc có kiểu các quy tắc của trợ lý; mỗi lần gọi lấy giá trị admin đang áp dụng.
@Component
@RequiredArgsConstructor
public class AssistantRules {

    private final BusinessSettings settings;

    public boolean enabled() {
        return settings.getBoolean(ENABLED);
    }

    /// Độ giống cosine tối thiểu, 0..1.
    public double minScore() {
        return settings.getInt(MIN_SCORE_PERCENT) / 100.0;
    }

    public int topK() {
        return settings.getInt(TOP_K);
    }

    public int questionsPerHour() {
        return settings.getInt(QUESTIONS_PER_HOUR);
    }

    public int historyTurns() {
        return settings.getInt(HISTORY_TURNS);
    }
}
