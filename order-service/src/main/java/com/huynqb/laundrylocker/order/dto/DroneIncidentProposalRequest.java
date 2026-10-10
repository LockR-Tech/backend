package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DroneIncidentProposalRequest(
        @NotBlank String resolutionType,
        Boolean redeliveryOffered,
        @PositiveOrZero BigDecimal compensationAmount,
        Boolean refundShippingFee,
        @Size(max = 1000) String overrideReason) {
}

