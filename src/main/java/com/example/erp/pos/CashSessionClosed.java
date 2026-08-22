package com.example.erp.pos;

import com.example.erp.shared.DomainEvent;
import com.example.erp.shared.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Publié à la clôture d'une session. Le module Finance l'écoute pour
 * créer l'écriture de réconciliation de caisse.
 */
public record CashSessionClosed(
        UUID eventId,
        Instant occurredAt,
        UUID cashSessionId,
        UUID tillId,
        Money expectedCash,
        Money countedCash,
        Money variance
) implements DomainEvent {

    public static CashSessionClosed of(UUID cashSessionId, UUID tillId,
                                       Money expectedCash, Money countedCash, Money variance) {
        return new CashSessionClosed(UUID.randomUUID(), Instant.now(),
                cashSessionId, tillId, expectedCash, countedCash, variance);
    }
}