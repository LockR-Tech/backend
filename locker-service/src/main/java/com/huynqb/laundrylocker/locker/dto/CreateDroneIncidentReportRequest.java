package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;

import java.util.List;

/** Phiếu sự cố do quản trị viên chủ động mở cho một drone. */
public record CreateDroneIncidentReportRequest(
        @NotBlank String title,
        @NotBlank String description,
        List<@Valid ReportAttachmentRequest> attachments) {
}
