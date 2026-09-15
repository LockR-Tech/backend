package com.huynqb.laundrylocker.locker.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.locker.dto.BoxLookupResponse;
import com.huynqb.laundrylocker.locker.dto.LockerResponse;
import com.huynqb.laundrylocker.locker.service.LockerLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// Tra cứu tủ/ô theo lô cho service khác (gateway chặn /internal từ ngoài).
@RestController
@RequiredArgsConstructor
public class InternalLockerLookupController {

    static final int MAX_IDS = 500;

    private final LockerLookupService lookupService;

    /// Không truyền `ids` ⇒ trả toàn bộ tủ.
    @GetMapping("/internal/lockers/batch")
    public ApiResponse<List<LockerResponse>> lockers(@RequestParam(required = false) List<Long> ids) {
        requireLimit(ids);
        return ApiResponse.ok(lookupService.lockers(ids));
    }

    @GetMapping("/internal/boxes/batch")
    public ApiResponse<List<BoxLookupResponse>> boxes(@RequestParam(required = false) List<Long> ids) {
        requireLimit(ids);
        return ApiResponse.ok(lookupService.boxes(ids));
    }

    private void requireLimit(List<Long> ids) {
        if (ids != null && ids.size() > MAX_IDS) {
            throw new BusinessException("TOO_MANY_IDS", "At most " + MAX_IDS + " ids per request");
        }
    }
}
