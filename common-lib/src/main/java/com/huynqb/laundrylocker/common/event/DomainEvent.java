package com.huynqb.laundrylocker.common.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DomainEvent(
        String eventId,
        String type,
        String source,
        Instant occurredAt,
        Map<String, Object> payload) implements Serializable {

    public static DomainEvent of(String type, String source, Map<String, Object> payload) {
        return new DomainEvent(UUID.randomUUID().toString(), type, source, Instant.now(), payload);
    }
}
