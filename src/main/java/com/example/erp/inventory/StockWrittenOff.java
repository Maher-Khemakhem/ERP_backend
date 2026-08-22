package com.example.erp.inventory;


import com.example.erp.shared.DomainEvent;
import com.example.erp.shared.Quantity;
import java.time.Instant;
import java.util.UUID;

/** Publié lors d'une sortie de stock hors vente : péremption, casse, perte. */
public record StockWrittenOff(
        UUID eventId,
        Instant occurredAt,
        UUID productId,
        UUID lotId,
        Quantity quantityWrittenOff,
        String reason
) implements DomainEvent {

    public static StockWrittenOff of(UUID productId, UUID lotId, Quantity quantity, String reason) {
        return new StockWrittenOff(UUID.randomUUID(), Instant.now(), productId, lotId, quantity, reason);
    }
}
