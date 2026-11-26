package com.huynqb.laundrylocker.notification.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.notification.dto.AdminNotificationBulkDeleteRequest;
import com.huynqb.laundrylocker.notification.dto.AdminNotificationStats;
import com.huynqb.laundrylocker.notification.dto.NotificationResponse;
import com.huynqb.laundrylocker.notification.service.AdminNotificationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// Trang Thông báo admin (gateway chỉ cho ADMIN vào `/api/admin/**`): bảng phân trang, thẻ số liệu, xoá
/// mềm hàng loạt. Thông báo ví cũ không hiện ở các API này.
@RestController
@RequiredArgsConstructor
public class AdminNotificationController {

    private final AdminNotificationQueryService queryService;

    /// `status` = READ | UNREAD; `search` tìm trong tiêu đề, nội dung hoặc mã thông báo, cộng thêm thông báo
    /// của `recipientIds` (người nhận có tên/email khớp, trang admin tự tra).
    @GetMapping("/api/admin/notifications/search")
    public ApiResponse<PageResponse<NotificationResponse>> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<Long> recipientIds) {
        return ApiResponse.ok(queryService.search(page, size, status, type, search, recipientIds));
    }

    @GetMapping("/api/admin/notifications/stats")
    public ApiResponse<AdminNotificationStats> stats() {
        return ApiResponse.ok(queryService.stats());
    }

    /// Trả mã các thông báo đã xoá mềm.
    @PostMapping("/api/admin/notifications/bulk-delete")
    public ApiResponse<List<Long>> bulkDelete(
            @RequestBody AdminNotificationBulkDeleteRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok("NOTIFICATIONS_DELETED", "Notifications deleted", queryService.bulkDelete(request, userId));
    }
}
