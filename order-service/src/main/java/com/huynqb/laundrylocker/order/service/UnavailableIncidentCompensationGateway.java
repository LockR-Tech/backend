package com.huynqb.laundrylocker.order.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class UnavailableIncidentCompensationGateway implements IncidentCompensationGateway {
    @Override
    public Submission submit(Long incidentId, Long orderId, Long beneficiaryUserId,
                             BigDecimal amount, boolean refundShippingFee, String idempotencyKey) {
        return new Submission(false, false, null,
                "Beneficiary compensation payout integration is unavailable");
    }
}
