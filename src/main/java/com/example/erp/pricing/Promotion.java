package com.example.erp.pricing;

import com.example.erp.shared.Money;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Promotion — aggregate root représentant une remise active sur un
 * produit (pourcentage, pour l'instant — bundle/BOGO viendront comme
 * des sous-types de PromotionType plus tard si besoin).
 *
 * Porte sa propre fenêtre de validité (validFrom/validUntil) et sait
 * elle-même déterminer si elle est applicable à un instant donné —
 * la responsabilité de vérifier l'éligibilité vit ICI, pas dans
 * PricingEngine, qui ne fait qu'interroger chaque promotion.
 */
@Entity
@Table(name = "promotion", schema = "pricing")
public class Promotion {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PromotionType type;

    @Column(name = "discount_percent", nullable = false)
    private BigDecimal discountPercent;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Column(nullable = false)
    private boolean active;

    protected Promotion() {
        // requis par JPA/Hibernate
    }

    private Promotion(UUID id, UUID productId, PromotionType type, BigDecimal discountPercent,
                      Instant validFrom, Instant validUntil) {
        this.id = Objects.requireNonNull(id);
        this.productId = Objects.requireNonNull(productId);
        this.type = Objects.requireNonNull(type);
        this.discountPercent = requireValidPercent(discountPercent);
        this.validFrom = Objects.requireNonNull(validFrom);
        this.validUntil = Objects.requireNonNull(validUntil);
        if (validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("validUntil ne peut pas précéder validFrom");
        }
        this.active = true;
    }

    public static Promotion percentageDiscount(UUID productId, BigDecimal discountPercent,
                                               Instant validFrom, Instant validUntil) {
        return new Promotion(UUID.randomUUID(), productId, PromotionType.PERCENTAGE_DISCOUNT,
                discountPercent, validFrom, validUntil);
    }

    private static BigDecimal requireValidPercent(BigDecimal percent) {
        Objects.requireNonNull(percent, "discountPercent must not be null");
        if (percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Le pourcentage de remise doit être entre 0 et 100 : " + percent);
        }
        return percent;
    }

    /** Vrai si cette promotion s'applique au produit donné, à l'instant donné. */
    public boolean appliesTo(UUID productId, Instant now) {
        return active
                && this.productId.equals(productId)
                && !now.isBefore(validFrom)
                && !now.isAfter(validUntil);
    }

    /** Applique la remise à un prix déjà calculé (avant remise). */
    public Money applyTo(Money price) {
        return price.applyDiscountPercent(discountPercent);
    }

    public void deactivate() {
        this.active = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public boolean isActive() {
        return active;
    }

    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Promotion other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public enum PromotionType {
        PERCENTAGE_DISCOUNT
        // BUNDLE, BOGO, LOYALTY_MULTIPLIER viendront comme des cas
        // supplémentaires ici si/quand on en a réellement besoin —
        // pas de spéculation prématurée sur des types non encore utilisés
    }
}