package com.huynqb.laundrylocker.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DroneIncidentProposalResponse(
        Long id, Integer version, String resolutionType, boolean redeliveryOffered,
        BigDecimal compensationAmount, boolean refundShippingFee, String policyVersion,
        String overrideReason, Long proposedByUserId, String status,
        String customerResponseNote, LocalDateTime respondedAt, LocalDateTime createdAt) {
}

