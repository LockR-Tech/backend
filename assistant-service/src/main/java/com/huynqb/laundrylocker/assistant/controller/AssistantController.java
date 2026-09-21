package com.huynqb.laundrylocker.assistant.controller;

import com.huynqb.laundrylocker.assistant.chat.AssistantService;
import com.huynqb.laundrylocker.assistant.chat.ChatDtos.*;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.security.UserRoles;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/// Hỏi đáp cho mọi người dùng đã đăng nhập (gateway yêu cầu JWT và gắn X-User-Id / X-User-Roles).
/// Vai trò quyết định tài liệu nào được dùng để trả lời.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    @GetMapping("/status")
    public ApiResponse<AssistantStatus> status() {
        return ApiResponse.ok(assistantService.status());
    }

    @PostMapping("/ask")
    public ApiResponse<AskResponse> ask(
            @Valid @RequestBody AskRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok(
                "ASSISTANT_ANSWERED", "Answered", assistantService.ask(userId, UserRoles.parse(roles), request));
    }

    @GetMapping("/conversations")
    public ApiResponse<List<ConversationView>> conversations(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(assistantService.myConversations(userId));
    }

    @GetMapping("/conversations/{id}")
    public ApiResponse<ConversationDetail> conversation(
            @PathVariable long id, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(assistantService.myConversation(id, userId));
    }

    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> deleteConversation(@PathVariable long id, @RequestHeader("X-User-Id") Long userId) {
        assistantService.deleteConversation(id, userId);
        return ApiResponse.ok("CONVERSATION_DELETED", "Conversation deleted");
    }
}
