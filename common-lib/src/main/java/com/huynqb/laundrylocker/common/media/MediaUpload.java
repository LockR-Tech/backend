package com.huynqb.laundrylocker.common.media;

/// Kết quả client nhận từ Cloudinary sau khi upload bằng chữ ký của server.
/// `signature` là chữ ký Cloudinary trả về trong phản hồi upload (không phải chữ ký upload),
/// server tự xác minh trước khi tin `publicId`/`version`.
public record MediaUpload(
        String publicId,
        Long version,
        String signature,
        String format,
        Long bytes,
        Integer width,
        Integer height) {
}
