package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/// KTV drone vào/ra ca và gửi vị trí. Trường nào null thì giữ nguyên.
public record DroneTechnicianPresenceRequest(
        Boolean onDuty,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude) {
}
