package com.example.erp.inventory;


import com.example.erp.shared.DomainEvent;
import com.example.erp.shared.Quantity;
import java.time.Instant;
import java.util.UUID;

/**
 * Publié après une vente réussie. Le module Finance l'utilise pour
 * enregistrer le chiffre d'affaires ; ne porte PAS le prix de vente
 * (qui appartient au module Pricing/POS) — uniquement le mouvement
 * physique de stock.
 */
public record StockSold(
        UUID eventId,
        Instant occurredAt,
        UUID productId,
        Quantity quantitySold
) implements DomainEvent {

    public static StockSold of(UUID productId, Quantity quantity) {
        return new StockSold(UUID.randomUUID(), Instant.now(), productId, quantity);
    }
}
