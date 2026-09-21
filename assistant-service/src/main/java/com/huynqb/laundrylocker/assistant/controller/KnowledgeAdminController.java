package com.huynqb.laundrylocker.assistant.controller;

import com.huynqb.laundrylocker.assistant.chat.AssistantService;
import com.huynqb.laundrylocker.assistant.chat.ChatDtos.ConversationDetail;
import com.huynqb.laundrylocker.assistant.chat.ChatDtos.ConversationView;
import com.huynqb.laundrylocker.assistant.eval.EvalService;
import com.huynqb.laundrylocker.assistant.eval.EvalService.EvalCaseRequest;
import com.huynqb.laundrylocker.assistant.knowledge.KnowledgeDocument;
import com.huynqb.laundrylocker.assistant.knowledge.KnowledgeService;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/// Quản lý kho tri thức của trợ lý. Gateway chỉ cho ADMIN vào /api/admin/**.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/knowledge")
public class KnowledgeAdminController {

    private final KnowledgeService knowledgeService;
    private final AssistantService assistantService;
    private final EvalService evalService;

    public record UpdateDocumentRequest(@Size(max = 255) String title, List<String> allowedRoles) {
    }

    /// `allowedRoles`: ALL (mặc định) hoặc một/nhiều trong CUSTOMER, LOCKER_TECHNICIAN, DRONE_TECHNICIAN, ADMIN.
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<KnowledgeDocument> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) List<String> allowedRoles,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException("DOCUMENT_UNREADABLE", "Không đọc được file tải lên");
        }
        return ApiResponse.ok(
                "KNOWLEDGE_DOCUMENT_UPLOADED", "Document uploaded; indexing in background",
                knowledgeService.upload(
                        file.getOriginalFilename(), file.getContentType(), content, title, allowedRoles, userId));
    }

    @GetMapping("/documents")
    public ApiResponse<List<KnowledgeDocument>> documents() {
        return ApiResponse.ok(knowledgeService.list());
    }

    @GetMapping("/documents/{id}")
    public ApiResponse<KnowledgeDocument> document(@PathVariable long id) {
        return ApiResponse.ok(knowledgeService.get(id));
    }

    @PutMapping("/documents/{id}")
    public ApiResponse<KnowledgeDocument> updateDocument(
            @PathVariable long id, @Valid @RequestBody UpdateDocumentRequest request) {
        return ApiResponse.ok(
                "KNOWLEDGE_DOCUMENT_UPDATED", "Document updated",
                knowledgeService.update(id, request.title(), request.allowedRoles()));
    }

    @PostMapping("/documents/{id}/reindex")
    public ApiResponse<KnowledgeDocument> reindex(@PathVariable long id) {
        return ApiResponse.ok("KNOWLEDGE_DOCUMENT_REQUEUED", "Document queued for indexing", knowledgeService.reindex(id));
    }

    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        knowledgeService.delete(id);
        return ApiResponse.ok("KNOWLEDGE_DOCUMENT_DELETED", "Document deleted");
    }

    @GetMapping("/conversations")
    public ApiResponse<List<ConversationView>> conversations(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(assistantService.recentConversations(userId, page, size));
    }

    @GetMapping("/conversations/{id}")
    public ApiResponse<ConversationDetail> conversation(@PathVariable long id) {
        return ApiResponse.ok(assistantService.conversation(id));
    }

    @GetMapping("/eval-cases")
    public ApiResponse<List<EvalService.EvalCase>> evalCases() {
        return ApiResponse.ok(evalService.list());
    }

    @PostMapping("/eval-cases")
    public ApiResponse<List<EvalService.EvalCase>> addEvalCases(
            @RequestBody List<@Valid EvalCaseRequest> requests) {
        return ApiResponse.ok("EVAL_CASES_ADDED", "Eval cases added", evalService.add(requests));
    }

    @DeleteMapping("/eval-cases/{id}")
    public ApiResponse<Void> deleteEvalCase(@PathVariable long id) {
        evalService.delete(id);
        return ApiResponse.ok("EVAL_CASE_DELETED", "Eval case deleted");
    }

    /// `generate=true` gọi Claude cho từng câu — tốn phí mô hình, chỉ chạy khi cần.
    @PostMapping("/eval")
    public ApiResponse<EvalService.EvalReport> runEval(@RequestParam(defaultValue = "false") boolean generate) {
        return ApiResponse.ok(evalService.run(generate));
    }
}
