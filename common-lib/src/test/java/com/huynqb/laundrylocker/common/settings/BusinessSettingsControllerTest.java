package com.huynqb.laundrylocker.common.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.huynqb.laundrylocker.common.exception.GlobalExceptionHandler;
import com.huynqb.laundrylocker.common.i18n.EnglishResponseAdvice;
import com.huynqb.laundrylocker.common.i18n.ServerText;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// Trang Cài đặt tiếng Anh: tên/mô tả/nhóm/đơn vị của quy tắc được dịch, giá trị admin soạn thì không.
class BusinessSettingsControllerTest {

    private static final String REASONS = "app.payment.refund-reasons";
    private static final String FEE = "app.order.send-base-fee";
    /// Chuỗi tiếng Việt có trong `server-text-en.json` — giá trị cũng trùng một câu có bản dịch, để chắc
    /// chắn giá trị không bị dịch theo.
    private static final SettingsCatalog CATALOG = () -> List.of(
            SettingDefinition.integer(FEE, "Giá & phí", "Phí gửi hàng qua tủ",
                    "Giá mỗi đơn gửi hàng qua tủ. Áp dụng cho đơn tạo sau khi lưu.", 15000, 0, 10_000_000, "VND"),
            SettingDefinition.integer("app.order.overdue-hours", "Thời hạn & tự động hoá", "Nhả ô quá hạn sau",
                    "", 24, 0, 720, "giờ"),
            SettingDefinition.string(REASONS, "Hoàn tiền & khiếu nại", "Danh sách lý do hoàn tiền gợi ý",
                    "Các lý do hoàn tiền hợp lệ hiển thị trên ứng dụng, phân tách bằng dấu phẩy.",
                    "Phương thức cũ", List.of()).asPublic());

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final BusinessSettings settings =
            new BusinessSettings("order", List.of(CATALOG), new InMemorySettingsStore(), new MockEnvironment());
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new BusinessSettingsController(settings))
            .addPlaceholderValue("app.settings.scope", "order")
            .setControllerAdvice(new GlobalExceptionHandler(), new EnglishResponseAdvice(mapper))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
            .build();

    @Test
    void englishTranslatesDefinitionTextButKeepsValues() throws Exception {
        mvc.perform(get("/api/admin/settings/order").header(ServerText.LANGUAGE_HEADER, "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].group").value("Prices & fees"))
                .andExpect(jsonPath("$.data[0].label").value("Locker send fee"))
                .andExpect(jsonPath("$.data[0].description")
                        .value("Price per locker delivery order. Applies to orders created after saving."))
                .andExpect(jsonPath("$.data[0].unit").value("VND"))
                .andExpect(jsonPath("$.data[0].value").value("15000"))
                .andExpect(jsonPath("$.data[1].label").value("Release overdue boxes after"))
                .andExpect(jsonPath("$.data[1].unit").value("hours"))
                .andExpect(jsonPath("$.data[2].value").value("Phương thức cũ"))
                .andExpect(jsonPath("$.data[2].defaultValue").value("Phương thức cũ"));
    }

    @Test
    void vietnameseIsUnchanged() throws Exception {
        mvc.perform(get("/api/admin/settings/order"))
                .andExpect(jsonPath("$.data[0].label").value("Phí gửi hàng qua tủ"))
                .andExpect(jsonPath("$.data[1].unit").value("giờ"));
    }

    @Test
    void savingInEnglishReturnsTranslatedDefinitionsAndRawValues() throws Exception {
        mvc.perform(put("/api/admin/settings/order").header(ServerText.LANGUAGE_HEADER, "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"" + REASONS + "\":\"Thanh toán trùng đơn\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[2].label").value("Suggested refund reasons"))
                .andExpect(jsonPath("$.data[2].value").value("Thanh toán trùng đơn"));
    }

    @Test
    void publicValuesAreNeverTranslated() throws Exception {
        mvc.perform(get("/api/settings/order/public").header(ServerText.LANGUAGE_HEADER, "en"))
                .andExpect(jsonPath("$.data['" + REASONS + "']").value("Phương thức cũ"));
    }

    @Test
    void validationErrorsFollowTheLanguage() throws Exception {
        String body = "{\"values\":{\"" + FEE + "\":-1}}";
        mvc.perform(put("/api/admin/settings/order").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETTING_INVALID"))
                .andExpect(jsonPath("$.message").value("«Phí gửi hàng qua tủ» phải ≥ 0"));
        mvc.perform(put("/api/admin/settings/order").header(ServerText.LANGUAGE_HEADER, "en")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("“Locker send fee” must be ≥ 0"));
    }
}
