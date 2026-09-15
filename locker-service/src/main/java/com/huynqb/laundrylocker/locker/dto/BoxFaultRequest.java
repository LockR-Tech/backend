package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BoxFaultRequest(
        @Size(max = 2000) String reason,
        @Size(max = 5) List<@Valid ReportAttachmentRequest> attachments) {
}
