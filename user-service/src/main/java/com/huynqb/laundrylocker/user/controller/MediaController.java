package com.huynqb.laundrylocker.user.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.common.media.MediaPurpose;
import com.huynqb.laundrylocker.common.media.UploadSignatureResponse;
import com.huynqb.laundrylocker.common.security.UserRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/// Cấp chữ ký để client upload ảnh thẳng lên Cloudinary (ADR-0004). Không nhận file.
/// Mọi tài khoản đăng nhập gọi được; purpose chỉ dành cho ADMIN bị chặn tại đây.
@RestController
@RequiredArgsConstructor
public class MediaController {

    private final CloudinaryMediaStorage mediaStorage;

    @PostMapping("/api/media/upload-signatures")
    public ApiResponse<UploadSignatureResponse> uploadSignatures(
            @RequestBody SignatureRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        MediaPurpose purpose = MediaPurpose.parse(request == null ? null : request.purpose());
        if (!purpose.isAllowedFor(UserRoles.parse(roles))) {
            throw new BusinessException(
                    "MEDIA_PURPOSE_FORBIDDEN", "Your role cannot upload " + purpose.name(), HttpStatus.FORBIDDEN);
        }
        int count = request.count() == null ? 1 : request.count();
        return ApiResponse.ok(
                "MEDIA_UPLOAD_SIGNED", "Upload signatures issued",
                mediaStorage.createUploadSignatures(purpose, userId, count));
    }

    public record SignatureRequest(String purpose, Integer count) {
    }
}
