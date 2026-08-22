package com.example.erp.pos;

import com.example.erp.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Publié quand un ticket est annulé avant finalisation. Contrairement
 * à TransactionCompleted, cet événement n'a AUCUN effet sur le stock
 * ou les finances — rien n'avait encore été déduit ou comptabilisé.
 * Utile principalement pour l'audit/traçabilité.
 */
public record TransactionVoided(
        UUID eventId,
        Instant occurredAt,
        UUID transactionId,
        String reason
) implements DomainEvent {

    public static TransactionVoided of(UUID transactionId, String reason) {
        return new TransactionVoided(UUID.randomUUID(), Instant.now(), transactionId, reason);
    }
}