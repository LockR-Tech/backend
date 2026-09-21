package com.huynqb.laundrylocker.assistant.controller;

import com.huynqb.laundrylocker.assistant.chat.AssistantService;
import com.huynqb.laundrylocker.assistant.eval.EvalService;
import com.huynqb.laundrylocker.assistant.knowledge.KnowledgeService;
import com.huynqb.laundrylocker.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// Dữ liệu sai trong body dạng danh sách phải trả 400 chứ không rơi vào handler chung (500).
class KnowledgeAdminControllerTest {

    private final EvalService evalService = mock(EvalService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders
                .standaloneSetup(new KnowledgeAdminController(
                        mock(KnowledgeService.class), mock(AssistantService.class), evalService))
                .setControllerAdvice(new AssistantExceptionHandler(), new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void blankEvalQuestionIsRejectedAsValidationError() throws Exception {
        mvc.perform(post("/api/admin/knowledge/eval-cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"question\":\"  \",\"mustRefuse\":true}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(evalService, never()).add(anyList());
    }

    @Test
    void validEvalCasesReachTheService() throws Exception {
        mvc.perform(post("/api/admin/knowledge/eval-cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"question\":\"Giá vàng hôm nay?\",\"mustRefuse\":true}]"))
                .andExpect(status().isOk());

        verify(evalService).add(anyList());
    }
}
