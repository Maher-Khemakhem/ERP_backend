package com.example.erp.procurement;

import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;

/**
 * PurchaseOrderLine — une ligne d'une commande fournisseur : produit,
 * quantité commandée, coût unitaire d'achat.
 *
 * Entité, accessible uniquement via l'aggregate root PurchaseOrder.
 * Structurellement proche de TransactionLine (POS), mais
 * délibérément un type distinct — voir la note dans PurchaseOrder sur
 * pourquoi on ne les fusionne pas.
 */
@Entity
@Table(name = "purchase_order_line", schema = "procurement")
public class PurchaseOrderLine {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Embedded
    private Quantity quantity;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "unit_cost"))
    private Money unitCost;

    protected PurchaseOrderLine() {
        // requis par JPA/Hibernate
    }

    private PurchaseOrderLine(UUID id, UUID productId, Quantity quantity, Money unitCost) {
        this.id = Objects.requireNonNull(id);
        this.productId = Objects.requireNonNull(productId);
        this.quantity = Objects.requireNonNull(quantity);
        this.unitCost = Objects.requireNonNull(unitCost);
    }

    public static PurchaseOrderLine of(UUID productId, Quantity quantity, Money unitCost) {
        if (unitCost.isNegative()) {
            throw new IllegalArgumentException("Le coût unitaire ne peut pas être négatif");
        }
        return new PurchaseOrderLine(UUID.randomUUID(), productId, quantity, unitCost);
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

    public Money getUnitCost() {
        return unitCost;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PurchaseOrderLine other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}