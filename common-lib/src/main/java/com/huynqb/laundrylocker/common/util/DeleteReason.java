package com.huynqb.laundrylocker.common.util;

import com.huynqb.laundrylocker.common.exception.BusinessException;

/// Lý do xoá mềm mà admin nhập: bắt buộc, lưu cùng bản ghi (cột `deleted_reason`, tối đa 500 ký tự).
public final class DeleteReason {

    public static final int MAX_LENGTH = 500;

    private DeleteReason() {
    }

    public static String require(String reason) {
        String value = reason == null ? "" : reason.trim();
        if (value.isEmpty()) {
            throw new BusinessException("DELETE_REASON_REQUIRED", "Vui lòng nhập lý do xóa");
        }
        if (value.length() > MAX_LENGTH) {
            throw new BusinessException(
                    "DELETE_REASON_TOO_LONG", "Lý do xóa không được dài quá " + MAX_LENGTH + " ký tự");
        }
        return value;
    }
}
