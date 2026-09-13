package com.huynqb.laundrylocker.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success, String code, String message, T data, List<ApiError> errors) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", null, data, null);
    }

    public static <T> ApiResponse<T> ok(String code, String message, T data) {
        return new ApiResponse<>(true, code, message, data, null);
    }

    public static ApiResponse<Void> ok(String code, String message) {
        return new ApiResponse<>(true, code, message, null, null);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null, null);
    }

    public static ApiResponse<Void> error(String code, String message, List<ApiError> errors) {
        return new ApiResponse<>(false, code, message, null, errors);
    }
}
