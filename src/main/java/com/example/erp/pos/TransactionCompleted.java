package com.example.erp.pos;

import com.example.erp.shared.DomainEvent;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * TransactionCompleted — l'événement central du système.
 *
 * Écouté par :
 *   - Inventory : déduit le stock de chaque ligne (FEFO)
 *   - Finance : enregistre le chiffre d'affaires
 *   - Pos lui-même (CashSession) : accumule l'encaissement si CASH
 *
 * Porte un INSTANTANÉ des lignes (LineItem, pas TransactionLine) —
 * volontairement découplé de l'entité JPA de PosTransaction, pour que
 * l'événement reste un fait immuable indépendant du cycle de vie de
 * l'aggregate qui l'a produit.
 */
public record TransactionCompleted(
        UUID eventId,
        Instant occurredAt,
        UUID transactionId,
        UUID cashSessionId,
        PosTransaction.PaymentMethod paymentMethod,
        Money total,
        List<LineItem> lines
) implements DomainEvent {

    public static TransactionCompleted of(UUID transactionId, UUID cashSessionId,
                                          PosTransaction.PaymentMethod paymentMethod,
                                          Money total, List<LineItem> lines) {
        return new TransactionCompleted(UUID.randomUUID(), Instant.now(),
                transactionId, cashSessionId, paymentMethod, total, lines);
    }

    /** Instantané immuable d'une ligne, indépendant de TransactionLine (l'entité JPA). */
    public record LineItem(UUID productId, Quantity quantity, Money lineTotal) {
    }
}