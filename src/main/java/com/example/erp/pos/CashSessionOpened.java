package com.example.erp.pos;

import com.example.erp.shared.DomainEvent;
import com.example.erp.shared.Money;
import java.time.Instant;
import java.util.UUID;

/** Publié à l'ouverture d'une session de caisse, avec son fond initial. */
public record CashSessionOpened(
        UUID eventId,
        Instant occurredAt,
        UUID cashSessionId,
        UUID tillId,
        Money openingFloat
) implements DomainEvent {

    public static CashSessionOpened of(UUID cashSessionId, UUID tillId, Money openingFloat) {
        return new CashSessionOpened(UUID.randomUUID(), Instant.now(), cashSessionId, tillId, openingFloat);
    }
}