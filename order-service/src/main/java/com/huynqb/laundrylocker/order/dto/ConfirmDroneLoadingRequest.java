package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmDroneLoadingRequest(
        @Min(1) @Max(100_000) int payloadWeightGrams,
        @NotBlank @Size(max = 80) String sealCode,
        @AssertTrue(message = "Parcel identity must be verified") boolean parcelMatched,
        @AssertTrue(message = "Payload must be secured") boolean payloadSecured,
        @AssertTrue(message = "Cargo compartment must be locked") boolean compartmentLocked,
        @Size(max = 500) String note) {
}
