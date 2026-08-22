package com.example.erp.inventory;


import com.example.erp.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Publié quand le stock disponible d'un produit passe sous son seuil
 * de réapprovisionnement. Le module Procurement l'écoute pour
 * suggérer/déclencher une commande fournisseur.
 */
public record LowStockThresholdReached(
        UUID eventId,
        Instant occurredAt,
        UUID productId
) implements DomainEvent {

    public static LowStockThresholdReached of(UUID productId) {
        return new LowStockThresholdReached(UUID.randomUUID(), Instant.now(), productId);
    }
}