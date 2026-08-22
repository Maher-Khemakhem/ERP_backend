package com.example.erp.pricing;

import com.example.erp.shared.Money;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;

/**
 * ProductPrice — le prix de référence actuel d'un produit.
 *
 * Entité simple, pas un aggregate root : un seul prix courant par
 * produit, pas d'historique de versions pour l'instant (on ajoutera
 * une notion de fenêtre de validité de prix plus tard si un vrai
 * besoin apparaît — pas de spéculation prématurée).
 */
@Entity
@Table(name = "product_price", schema = "pricing")
public class ProductPrice {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false, unique = true)
    private UUID productId;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "unit_price"))
    private Money unitPrice;

    protected ProductPrice() {
        // requis par JPA/Hibernate
    }

    private ProductPrice(UUID id, UUID productId, Money unitPrice) {
        this.id = Objects.requireNonNull(id);
        this.productId = Objects.requireNonNull(productId);
        this.unitPrice = Objects.requireNonNull(unitPrice);
    }

    public static ProductPrice of(UUID productId, Money unitPrice) {
        if (unitPrice.isNegative()) {
            throw new IllegalArgumentException("Le prix ne peut pas être négatif");
        }
        return new ProductPrice(UUID.randomUUID(), productId, unitPrice);
    }

    public void updatePrice(Money newPrice) {
        if (newPrice.isNegative()) {
            throw new IllegalArgumentException("Le prix ne peut pas être négatif");
        }
        this.unitPrice = newPrice;
    }

    public UUID getProductId() {
        return productId;
    }

    public Money unitPrice() {
        return unitPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductPrice other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}