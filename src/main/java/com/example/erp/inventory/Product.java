package com.example.erp.inventory;

import com.example.erp.shared.Quantity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "product", schema = "inventory")
public class Product {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private ProductFamily family;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Quantity.UnitType unitType;

    private boolean perishable;

    @Column(name = "reorder_point")
    private int reorderPoint;

    @Column(name = "vat_rate", precision = 5, scale = 2)
    private BigDecimal vatRate;

    protected Product() {
        // requis par JPA/Hibernate
    }

    private Product(UUID id, String sku, String name, ProductFamily family, String imageUrl,
                    Quantity.UnitType unitType, boolean perishable, int reorderPoint,
                    BigDecimal vatRate) {
        this.id = Objects.requireNonNull(id);
        this.sku = requireNonBlank(sku, "sku");
        this.name = requireNonBlank(name, "name");
        this.family = family;
        this.imageUrl = normalizeOptional(imageUrl);
        this.unitType = Objects.requireNonNull(unitType);
        this.perishable = perishable;
        this.reorderPoint = reorderPoint;
        this.vatRate = requireVatRate(vatRate);
    }

    public static Product create(String sku, String name, Quantity.UnitType unitType,
                                 boolean perishable, int reorderPoint) {
        return create(sku, name, unitType, perishable, reorderPoint, BigDecimal.valueOf(19));
    }

    public static Product create(String sku, String name, Quantity.UnitType unitType,
                                 boolean perishable, int reorderPoint, BigDecimal vatRate) {
        return create(sku, name, null, null, unitType, perishable, reorderPoint, vatRate);
    }

    public static Product create(String sku, String name, ProductFamily family, String imageUrl,
                                 Quantity.UnitType unitType, boolean perishable, int reorderPoint,
                                 BigDecimal vatRate) {
        if (reorderPoint < 0) {
            throw new IllegalArgumentException("Le seuil de réapprovisionnement ne peut pas être négatif");
        }
        return new Product(UUID.randomUUID(), sku, name, family, imageUrl, unitType, perishable, reorderPoint, vatRate);
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " ne peut pas être vide");
        }
        return value;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal requireVatRate(BigDecimal value) {
        Objects.requireNonNull(value, "vatRate must not be null");
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("VAT rate must be between 0 and 100");
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public ProductFamily getFamily() {
        return family;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Quantity.UnitType getUnitType() {
        return unitType;
    }

    public boolean isPerishable() {
        return perishable;
    }

    public int getReorderPoint() {
        return reorderPoint;
    }

    public BigDecimal getVatRate() {
        return vatRate == null ? BigDecimal.valueOf(19) : vatRate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
