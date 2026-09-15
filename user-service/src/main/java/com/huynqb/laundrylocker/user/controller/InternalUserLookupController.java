package com.huynqb.laundrylocker.user.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// Tra cứu người dùng theo lô cho service khác (gateway chặn /internal từ ngoài).
@RestController
@RequiredArgsConstructor
public class InternalUserLookupController {

    static final int MAX_IDS = 500;

    private final UserProfileService userProfileService;

    @GetMapping("/internal/users/batch")
    public ApiResponse<List<UserSummary>> batch(@RequestParam(required = false) List<Long> ids) {
        if (ids != null && ids.size() > MAX_IDS) {
            throw new BusinessException("TOO_MANY_IDS", "At most " + MAX_IDS + " ids per request");
        }
        return ApiResponse.ok(userProfileService.getMany(ids));
    }
}
