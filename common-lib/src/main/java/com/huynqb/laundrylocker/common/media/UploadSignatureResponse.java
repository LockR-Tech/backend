package com.huynqb.laundrylocker.common.media;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record UploadSignatureResponse(
        String provider,
        String cloudName,
        String uploadUrl,
        long maxBytes,
        List<String> allowedFormats,
        Instant expiresAt,
        List<SignedUpload> uploads) {

    /// `fields` phải được gửi nguyên vẹn cùng `file` trong multipart tới `uploadUrl`.
    public record SignedUpload(String publicId, Map<String, String> fields) {
    }
}
