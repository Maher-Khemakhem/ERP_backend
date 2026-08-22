package com.example.erp.pos;

import com.example.erp.shared.DomainEvent;
import com.example.erp.shared.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Publié uniquement s'il y a un écart (positif ou négatif) entre le
 * comptage réel et le montant attendu à la clôture — signal distinct
 * de CashSessionClosed pour permettre une alerte/investigation ciblée
 * sans avoir à réanalyser chaque clôture normale.
 */
public record TillOverShortDetected(
        UUID eventId,
        Instant occurredAt,
        UUID cashSessionId,
        UUID tillId,
        Money variance
) implements DomainEvent {

    public static TillOverShortDetected of(UUID cashSessionId, UUID tillId, Money variance) {
        return new TillOverShortDetected(UUID.randomUUID(), Instant.now(), cashSessionId, tillId, variance);
    }
}