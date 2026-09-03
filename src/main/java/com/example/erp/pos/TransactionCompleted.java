package com.example.erp.pos;

import com.example.erp.shared.DomainEvent;
import com.example.erp.shared.Money;
import java.time.Instant;
import java.util.UUID;

/** Immutable financial snapshot of a completed sale. */
public record TransactionCompleted(
        UUID eventId, Instant occurredAt, UUID transactionId, UUID cashSessionId,
        String paymentMethod, Money netAmount, Money vatAmount,
        Money grossAmount, Money costOfGoodsSold
) implements DomainEvent {
    public static TransactionCompleted of(UUID transactionId, UUID cashSessionId, String paymentMethod,
                                          Money netAmount, Money vatAmount, Money grossAmount,
                                          Money costOfGoodsSold) {
        return new TransactionCompleted(UUID.randomUUID(), Instant.now(), transactionId, cashSessionId,
                paymentMethod, netAmount, vatAmount, grossAmount, costOfGoodsSold);
    }
}
