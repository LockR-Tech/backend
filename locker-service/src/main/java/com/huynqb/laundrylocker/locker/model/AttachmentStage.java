package com.huynqb.laundrylocker.locker.model;

/// Giai đoạn của ảnh trong vòng đời phiếu sự cố.
public enum AttachmentStage {
    /// Người báo chụp tại hiện trường khi gửi phiếu.
    REPORT,
    /// KTV tới nơi chụp xác nhận hiện trạng.
    INSPECTION,
    /// Trong quá trình sửa, gắn với một dòng nhật ký.
    PROGRESS,
    /// Nghiệm thu sau khi sửa xong.
    RESOLUTION
}
