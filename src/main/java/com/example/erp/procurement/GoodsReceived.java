package com.example.erp.procurement;

import com.example.erp.shared.DomainEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Publié à la réception physique de la marchandise. Écouté par
 * Inventory pour créer les Lot correspondants et augmenter le stock —
 * Procurement ne connaît pas les dates de péremption des lots reçus,
 * c'est une information saisie séparément au moment de la réception
 * (typiquement par l'employé qui réceptionne, pas déductible de la
 * commande elle-même).
 */
public record GoodsReceived(
        UUID eventId,
        Instant occurredAt,
        UUID purchaseOrderId,
        UUID supplierId,
        List<PurchaseOrderPlaced.LineItem> lines
) implements DomainEvent {

    public static GoodsReceived of(UUID purchaseOrderId, UUID supplierId,
                                   List<PurchaseOrderPlaced.LineItem> lines) {
        return new GoodsReceived(UUID.randomUUID(), Instant.now(), purchaseOrderId, supplierId, lines);
    }
}