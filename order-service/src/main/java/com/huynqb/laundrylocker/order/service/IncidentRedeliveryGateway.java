package com.huynqb.laundrylocker.order.service;

/** Adapter boundary for creating a linked, charge-free delivery attempt. */
public interface IncidentRedeliveryGateway {
    Submission createAttempt(Long incidentId, Long originalOrderId, Long customerUserId,
                             String idempotencyKey);

    record Submission(boolean integrationAvailable, boolean accepted, Long deliveryOrderId, String detail) {
    }
}
