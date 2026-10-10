package com.huynqb.laundrylocker.order.service;

import org.springframework.stereotype.Component;

@Component
public class UnavailableIncidentRedeliveryGateway implements IncidentRedeliveryGateway {
    @Override
    public Submission createAttempt(Long incidentId, Long originalOrderId, Long customerUserId,
                                    String idempotencyKey) {
        return new Submission(false, false, null,
                "Linked redelivery-order integration is unavailable");
    }
}
