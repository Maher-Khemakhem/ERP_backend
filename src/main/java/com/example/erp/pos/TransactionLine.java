package com.example.erp.pos;

import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;

/**
 * TransactionLine — une ligne d'un ticket de caisse : un produit, une
 * quantité (unités ou poids), et le prix final de la ligne (déjà
 * calculé par PricingEngine au moment du scan, remises incluses).
 *
 * Entité, accessible uniquement via l'aggregate root PosTransaction.
 * Ne recalcule jamais le prix elle-même — le prix qu'elle porte est
 * une décision déjà prise par le module Pricing au moment du scan.
 */
@Entity
@Table(name = "transaction_line", schema = "pos")
public class TransactionLine {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Embedded
    private Quantity quantity;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "line_total"))
    private Money lineTotal; // déjà net de toute remise appliquée

    protected TransactionLine() {
        // requis par JPA/Hibernate
    }

    private TransactionLine(UUID id, UUID productId, Quantity quantity, Money lineTotal) {
        this.id = Objects.requireNonNull(id);
        this.productId = Objects.requireNonNull(productId);
        this.quantity = Objects.requireNonNull(quantity);
        this.lineTotal = Objects.requireNonNull(lineTotal);
    }

    /**
     * Crée une ligne à partir d'un prix DÉJÀ calculé par PricingEngine.
     * TransactionLine ne fait aucun calcul de prix lui-même — ce n'est
     * pas sa responsabilité (Single Responsibility appliqué au niveau
     * du modèle de domaine, pas seulement des classes de service).
     */
    public static TransactionLine of(UUID productId, Quantity quantity, Money calculatedPrice) {
        if (calculatedPrice.isNegative()) {
            throw new IllegalArgumentException("Le prix d'une ligne ne peut pas être négatif");
        }
        return new TransactionLine(UUID.randomUUID(), productId, quantity, calculatedPrice);
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public Quantity getQuantity() {
        return quantity;
    }

    public Money getLineTotal() {
        return lineTotal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransactionLine other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}