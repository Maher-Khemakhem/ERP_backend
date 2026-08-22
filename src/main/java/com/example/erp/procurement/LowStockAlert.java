package com.example.erp.procurement;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * LowStockAlert — une entrée dans la liste que le manager consulte au
 * back-office pour voir quels produits ont besoin d'être réapprovisionnés.
 *
 * Volontairement PAS un aggregate complexe : un signal "ce produit
 * est bas" avec un statut simple (visible / acquitté). Le manager
 * décide lui-même s'il crée une commande, quand, et auprès de quel
 * fournisseur — ce n'est pas automatisé.
 */
@Entity
@Table(name = "low_stock_alert", schema = "procurement")
public class LowStockAlert {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    @Column(nullable = false)
    private boolean acknowledged;

    protected LowStockAlert() {
        // requis par JPA/Hibernate
    }

    private LowStockAlert(UUID id, UUID productId) {
        this.id = Objects.requireNonNull(id);
        this.productId = Objects.requireNonNull(productId);
        this.raisedAt = Instant.now();
        this.acknowledged = false;
    }

    public static LowStockAlert raise(UUID productId) {
        return new LowStockAlert(UUID.randomUUID(), productId);
    }

    /** Le manager marque l'alerte comme traitée. */
    public void acknowledge() {
        this.acknowledged = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LowStockAlert other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}