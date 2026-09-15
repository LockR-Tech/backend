package com.huynqb.laundrylocker.common.media;

import com.huynqb.laundrylocker.common.exception.BusinessException;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

/// Mục đích của một ảnh — quyết định thư mục trên Cloudinary và vai trò được xin chữ ký upload.
public enum MediaPurpose {
    AVATAR("avatars", false),
    REPORT_EVIDENCE("reports", false),
    STORE_IMAGE("stores", true),
    PROMOTION_IMAGE("promotions", true);

    private final String folder;
    private final boolean adminOnly;

    MediaPurpose(String folder, boolean adminOnly) {
        this.folder = folder;
        this.adminOnly = adminOnly;
    }

    public String folder() {
        return folder;
    }

    public boolean isAllowedFor(Collection<String> roles) {
        return !adminOnly || (roles != null && roles.contains("ADMIN"));
    }

    public static MediaPurpose parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("MEDIA_PURPOSE_INVALID", "purpose is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(purpose -> purpose.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "MEDIA_PURPOSE_INVALID", "Unsupported media purpose: " + value));
    }
}
