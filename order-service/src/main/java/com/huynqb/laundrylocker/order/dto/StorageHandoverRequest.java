package com.huynqb.laundrylocker.order.dto;

import com.huynqb.laundrylocker.common.media.MediaUpload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/// Admin trao đồ lưu kho tại quầy.
/// - `recipientType` SENDER | RECEIVER | DELEGATE: `phoneLast4` là 4 số cuối SĐT người tới nhận đọc,
///   server so với SĐT của bên đó trên đơn.
/// - `recipientType` OTHER (người không có trên đơn): bắt buộc `recipientName`, `recipientPhone` và `note`.
/// - `photo`: ảnh xác minh (purpose STORAGE_HANDOVER), bắt buộc.
/// - Khách còn nợ: bắt buộc `collectedAmount` ≥ số còn nợ và `collectionMethod` CASH | TRANSFER.
public record StorageHandoverRequest(
        @NotBlank String recipientType,
        @Size(max = 4) String phoneLast4,
        @Size(max = 120) String recipientName,
        @Size(max = 20) String recipientPhone,
        @NotNull @Valid MediaUpload photo,
        @PositiveOrZero BigDecimal collectedAmount,
        String collectionMethod,
        @Size(max = 500) String note) {
}
