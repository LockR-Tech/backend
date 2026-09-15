package com.huynqb.laundrylocker.store.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.store.dto.StoreResponse;
import com.huynqb.laundrylocker.store.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// Tra cứu cửa hàng theo lô cho service khác (gateway chặn /internal từ ngoài).
@RestController
@RequiredArgsConstructor
public class InternalStoreLookupController {

    static final int MAX_IDS = 500;

    private final StoreService storeService;

    /// Không truyền `ids` ⇒ trả toàn bộ cửa hàng.
    @GetMapping("/internal/stores/batch")
    public ApiResponse<List<StoreResponse>> batch(@RequestParam(required = false) List<Long> ids) {
        if (ids != null && ids.size() > MAX_IDS) {
            throw new BusinessException("TOO_MANY_IDS", "At most " + MAX_IDS + " ids per request");
        }
        return ApiResponse.ok(storeService.getMany(ids));
    }
}
