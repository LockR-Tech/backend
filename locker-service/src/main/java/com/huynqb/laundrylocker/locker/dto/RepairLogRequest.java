package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RepairLogRequest(
        @NotBlank @Size(max = 2000) String note,
        @Size(max = 10) List<@Valid ReportAttachmentRequest> attachments) {
}
