package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/// Kết quả một mục trong checklist kiểm tra định kỳ: PASS / FAIL / NA (không áp dụng).
public record InspectionItemResult(
        @NotBlank @Size(max = 255) String label,
        @NotBlank String result,
        @Size(max = 1000) String note) {
}
