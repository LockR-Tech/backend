package com.huynqb.laundrylocker.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class Probe {
        @PostMapping("/probe")
        ResponseEntity<String> probe(@RequestHeader("Idempotency-Key") String key) {
            return ResponseEntity.ok(key);
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Probe())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    /// Tạo đơn drone thiếu `Idempotency-Key` từng trả 500 INTERNAL_ERROR (test 2026-10-29).
    @Test
    void missingRequiredHeaderIsAClientError() throws Exception {
        mvc.perform(post("/probe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HEADER_REQUIRED"));
    }
}
