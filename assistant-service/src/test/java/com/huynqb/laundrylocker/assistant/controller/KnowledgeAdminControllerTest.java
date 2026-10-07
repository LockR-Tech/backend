package com.huynqb.laundrylocker.assistant.controller;

import com.huynqb.laundrylocker.assistant.chat.AssistantService;
import com.huynqb.laundrylocker.assistant.eval.EvalService;
import com.huynqb.laundrylocker.assistant.knowledge.KnowledgeChunk;
import com.huynqb.laundrylocker.assistant.knowledge.KnowledgeDocument;
import com.huynqb.laundrylocker.assistant.knowledge.KnowledgeService;
import com.huynqb.laundrylocker.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// Lớp HTTP của kho tri thức: body danh sách sai trả 400 chứ không rơi vào handler chung (500);
/// file gốc và các đoạn đã đánh chỉ mục trả đúng dạng.
class KnowledgeAdminControllerTest {

    private final KnowledgeService knowledgeService = mock(KnowledgeService.class);
    private final EvalService evalService = mock(EvalService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders
                .standaloneSetup(new KnowledgeAdminController(
                        knowledgeService, mock(AssistantService.class), evalService))
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

    /// File gốc luôn tải về (attachment + nosniff) để HTML tải lên không chạy như trang của API.
    @Test
    void originalFileIsServedAsAttachment() throws Exception {
        byte[] html = "<h1>Chính sách</h1><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        when(knowledgeService.content(5L)).thenReturn(
                new KnowledgeDocument.Content(5L, "Chính sách", "chính-sách.html", "text/html", html));

        mvc.perform(get("/api/admin/knowledge/documents/5/file"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename*=UTF-8''ch%C3%ADnh-s%C3%A1ch.html")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(html));
    }

    @Test
    void chunksAreListedInDocumentOrder() throws Exception {
        when(knowledgeService.chunks(5L)).thenReturn(List.of(
                new KnowledgeChunk(11L, 0, "Hoàn tiền", "Hoàn tiền trong 7 ngày làm việc."),
                new KnowledgeChunk(12L, 1, null, "Liên hệ bộ phận hỗ trợ.")));

        mvc.perform(get("/api/admin/knowledge/documents/5/chunks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].heading").value("Hoàn tiền"))
                .andExpect(jsonPath("$.data[1].ordinal").value(1));
    }
}
