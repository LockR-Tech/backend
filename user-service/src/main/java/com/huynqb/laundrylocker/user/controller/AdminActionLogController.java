package com.huynqb.laundrylocker.user.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.common.i18n.KeepServerText;
import com.huynqb.laundrylocker.user.service.ActionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/// Nhật ký thao tác (F3-G16) cho web admin. Gateway chỉ cho ADMIN vào `/api/admin/**`.
/// Nội dung là dữ liệu đã ghi (tên người, mã tủ, lý do người dùng nhập) ⇒ không dịch.
@RestController
@RequiredArgsConstructor
public class AdminActionLogController {

    private final ActionLogService service;

    /// `actions`: mã hành động, nhiều mã cách dấu phẩy, mã kết thúc bằng `_` = tiền tố (vd `LOCKER_`).
    /// `actorUserId=0` = chỉ việc hệ thống tự làm, `-1` = chỉ việc do người bấm. `from`/`to`: ngày (yyyy-MM-dd), tính cả hai đầu.
    @KeepServerText
    @GetMapping("/api/admin/audit-logs")
    public ApiResponse<PageResponse<ActionLogService.ActionLogView>> search(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String actions,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Long lockerId,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q) {
        return ApiResponse.ok(service.search(page, size, actions, targetType, targetId, lockerId, actorUserId, from, to, q));
    }
}
