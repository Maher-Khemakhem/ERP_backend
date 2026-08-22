package com.example.erp.inventory;


import com.example.erp.shared.DomainEvent;
import com.example.erp.shared.Quantity;
import java.time.Instant;
import java.util.UUID;

/** Publié quand un nouveau lot est réceptionné et ajouté au stock. */
public record StockReceived(
        UUID eventId,
        Instant occurredAt,
        UUID productId,
        UUID lotId,
        Quantity quantityReceived
) implements DomainEvent {

    public static StockReceived of(UUID productId, UUID lotId, Quantity quantity) {
        return new StockReceived(UUID.randomUUID(), Instant.now(), productId, lotId, quantity);
    }
}