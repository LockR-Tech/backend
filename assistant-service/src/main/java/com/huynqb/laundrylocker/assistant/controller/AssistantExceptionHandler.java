package com.huynqb.laundrylocker.assistant.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/// Lỗi riêng của assistant-service mà handler chung (common-lib) sẽ trả 500. Đứng trước handler chung
/// vì Spring chọn advice đầu tiên có phương thức khớp — kể cả `@ExceptionHandler(Exception.class)`.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AssistantExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> tooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("DOCUMENT_TOO_LARGE", "File tối đa 20 MB"));
    }

    /// `List<@Valid …>` trong body được kiểm bằng method validation (Spring 6.1+).
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> invalid(HandlerMethodValidationException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", "Dữ liệu gửi lên không hợp lệ"));
    }
}
