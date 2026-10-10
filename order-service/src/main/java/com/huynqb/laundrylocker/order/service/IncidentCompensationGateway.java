package com.huynqb.laundrylocker.order.service;

import java.math.BigDecimal;

/** Adapter boundary for a real beneficiary payout provider. */
public interface IncidentCompensationGateway {
    Submission submit(Long incidentId, Long orderId, Long beneficiaryUserId,
                      BigDecimal amount, boolean refundShippingFee, String idempotencyKey);

    record Submission(boolean integrationAvailable, boolean accepted, String reference, String detail) {
    }
}
