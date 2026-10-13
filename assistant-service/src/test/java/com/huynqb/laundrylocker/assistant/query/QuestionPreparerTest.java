package com.huynqb.laundrylocker.assistant.query;

import com.huynqb.laundrylocker.assistant.provider.DiacriticsRestorer;
import com.huynqb.laundrylocker.assistant.provider.ProviderException;
import com.huynqb.laundrylocker.assistant.query.QuestionPreparer.PreparedQuestion;
import com.huynqb.laundrylocker.assistant.settings.AssistantRules;
import com.huynqb.laundrylocker.assistant.settings.AssistantSettingsCatalog;
import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class QuestionPreparerTest {

    private static final String UNACCENTED = "lam sao de gui hang cho nguoi khac qua tu?";
    private static final String RESTORED = "Làm sao để gửi hàng cho người khác qua tủ?";

    private final DiacriticsRestorer restorer = mock(DiacriticsRestorer.class);

    @BeforeEach
    void setUp() {
        when(restorer.configured()).thenReturn(true);
    }

    @Test
    void restoresUnaccentedVietnamese() {
        when(restorer.restore(UNACCENTED)).thenReturn(RESTORED);

        PreparedQuestion prepared = preparer(true).prepare(UNACCENTED);

        assertTrue(prepared.restored());
        assertEquals(RESTORED, prepared.text());
        assertEquals(UNACCENTED, prepared.original());
    }

    @Test
    void providerFailureFallsBackToTheOriginal() {
        when(restorer.restore(anyString())).thenThrow(new ProviderException("timeout"));

        PreparedQuestion prepared = preparer(true).prepare(UNACCENTED);

        assertFalse(prepared.restored());
        assertEquals(UNACCENTED, prepared.text());
    }

    @Test
    void unexpectedErrorAlsoFallsBack() {
        when(restorer.restore(anyString())).thenThrow(new IllegalStateException("boom"));

        assertEquals(UNACCENTED, preparer(true).prepare(UNACCENTED).text());
    }

    /// Mô hình trả lời câu hỏi (hoặc đổi nội dung) thay vì chỉ thêm dấu ⇒ bỏ kết quả.
    @Test
    void resultThatChangesTheWordingIsDiscarded() {
        when(restorer.restore(anyString())).thenReturn("Bạn mở ứng dụng, chọn Gửi hàng rồi chọn tủ.");

        PreparedQuestion prepared = preparer(true).prepare(UNACCENTED);

        assertFalse(prepared.restored());
        assertEquals(UNACCENTED, prepared.text());
    }

    @Test
    void blankResultIsDiscarded() {
        when(restorer.restore(anyString())).thenReturn("  ");

        assertEquals(UNACCENTED, preparer(true).prepare(UNACCENTED).text());
    }

    @Test
    void accentedOrEnglishQuestionsAreNotSent() {
        QuestionPreparer preparer = preparer(true);

        assertEquals(RESTORED, preparer.prepare(RESTORED).text());
        assertEquals("How do I rent a locker?", preparer.prepare("How do I rent a locker?").text());
        verify(restorer, never()).restore(anyString());
    }

    @Test
    void settingOffSkipsRestoration() {
        assertEquals(UNACCENTED, preparer(false).prepare(UNACCENTED).text());
        verify(restorer, never()).restore(anyString());
    }

    @Test
    void missingApiKeySkipsRestoration() {
        when(restorer.configured()).thenReturn(false);

        assertEquals(UNACCENTED, preparer(true).prepare(UNACCENTED).text());
        verify(restorer, never()).restore(anyString());
    }

    private QuestionPreparer preparer(boolean restore) {
        AssistantRules rules = new AssistantRules(BusinessSettings.inMemory(
                "assistant", new AssistantSettingsCatalog(),
                Map.of(AssistantSettingsCatalog.RESTORE_DIACRITICS, restore)));
        return new QuestionPreparer(restorer, rules);
    }
}
