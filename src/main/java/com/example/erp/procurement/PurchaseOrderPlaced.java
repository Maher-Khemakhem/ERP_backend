package com.example.erp.procurement;

import com.example.erp.shared.DomainEvent;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Publié quand une commande est envoyée au fournisseur. */
public record PurchaseOrderPlaced(
        UUID eventId,
        Instant occurredAt,
        UUID purchaseOrderId,
        UUID supplierId,
        List<LineItem> lines
) implements DomainEvent {

    public static PurchaseOrderPlaced of(UUID purchaseOrderId, UUID supplierId, List<LineItem> lines) {
        return new PurchaseOrderPlaced(UUID.randomUUID(), Instant.now(), purchaseOrderId, supplierId, lines);
    }

    public record LineItem(UUID productId, Quantity quantity, Money unitCost) {
    }
}