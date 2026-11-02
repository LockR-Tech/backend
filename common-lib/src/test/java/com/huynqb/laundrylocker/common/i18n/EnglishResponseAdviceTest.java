package com.huynqb.laundrylocker.common.i18n;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EnglishResponseAdviceTest {

    @RestController
    static class Probe {
        @GetMapping("/notifications")
        ApiResponse<List<Map<String, Object>>> notifications() {
            return ApiResponse.ok(List.of(Map.of(
                    "title", "Thanh toán thành công",
                    "createdAt", LocalDateTime.of(2026, 11, 2, 9, 30))));
        }

        @GetMapping("/fail")
        ApiResponse<Void> fail() {
            throw new BusinessException("INSUFFICIENT_BALANCE", "Số dư ví không đủ");
        }
    }

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Probe())
            .setControllerAdvice(new GlobalExceptionHandler(), new EnglishResponseAdvice(mapper))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
            .build();

    @Test
    void englishHeaderTranslatesBodyAndErrors() throws Exception {
        mvc.perform(get("/notifications").header(ServerText.LANGUAGE_HEADER, "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Payment successful"))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-11-02T09:30:00"));
        mvc.perform(get("/fail").header(ServerText.LANGUAGE_HEADER, "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.message").value("Insufficient wallet balance"));
    }

    @Test
    void withoutHeaderNothingChanges() throws Exception {
        mvc.perform(get("/notifications"))
                .andExpect(jsonPath("$.data[0].title").value("Thanh toán thành công"));
        mvc.perform(get("/fail").header(ServerText.LANGUAGE_HEADER, "vi"))
                .andExpect(jsonPath("$.message").value("Số dư ví không đủ"));
    }
}
