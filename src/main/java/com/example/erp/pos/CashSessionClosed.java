package com.example.erp.pos;

import com.example.erp.shared.DomainEvent;
import com.example.erp.shared.Money;
import java.time.Instant;
import java.util.UUID;

/** Snapshot emitted when a till is reconciled. */
public record CashSessionClosed(
        UUID eventId, Instant occurredAt, UUID cashSessionId,
        Money expectedCash, Money countedCash
) implements DomainEvent {
    public static CashSessionClosed of(UUID cashSessionId, Money expectedCash, Money countedCash) {
        return new CashSessionClosed(UUID.randomUUID(), Instant.now(), cashSessionId, expectedCash, countedCash);
    }
}
