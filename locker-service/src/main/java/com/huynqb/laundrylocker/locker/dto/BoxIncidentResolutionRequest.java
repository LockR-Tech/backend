package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BoxIncidentResolutionRequest(
        @NotNull Long boxId,
        @NotBlank String action, // "QUICK_FIX" | "RELOCATE" | "HANDOVER" | "HUB_ESCROW" | "LOCK_ONLY"
        Long targetBoxId,
        String reason,
        String customerOtp,
        String sealNumber,
        List<ReportAttachmentRequest> attachments
) {}
