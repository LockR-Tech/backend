package com.huynqb.laundrylocker.common.media;

/// Ảnh đã được xác minh là do chính người dùng upload lên Cloudinary của hệ thống.
/// `secureUrl` do server dựng, không lấy từ client.
public record VerifiedMedia(
        String publicId,
        String secureUrl,
        String format,
        Long bytes,
        Integer width,
        Integer height) {
}
