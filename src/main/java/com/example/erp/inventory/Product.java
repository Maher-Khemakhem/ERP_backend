package com.example.erp.inventory;

import com.example.erp.shared.Quantity;
import jakarta.persistence.*;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Quantity.UnitType unitType;

    private boolean perishable;

    @Column(name = "reorder_point")
    private int reorderPoint;

    protected Product() {
        // requis par JPA/Hibernate
    }

    private Product(UUID id, String sku, String name,
                    Quantity.UnitType unitType, boolean perishable, int reorderPoint) {
        this.id = Objects.requireNonNull(id);
        this.sku = requireNonBlank(sku, "sku");
        this.name = requireNonBlank(name, "name");
        this.unitType = Objects.requireNonNull(unitType);
        this.perishable = perishable;
        this.reorderPoint = reorderPoint;
    }

    public static Product create(String sku, String name, Quantity.UnitType unitType,
                                 boolean perishable, int reorderPoint) {
        if (reorderPoint < 0) {
            throw new IllegalArgumentException("Le seuil de réapprovisionnement ne peut pas être négatif");
        }
        return new Product(UUID.randomUUID(), sku, name, unitType, perishable, reorderPoint);
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " ne peut pas être vide");
        }
        return value;
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

    public Quantity.UnitType getUnitType() {
        return unitType;
    }

    public boolean isPerishable() {
        return perishable;
    }

    public int getReorderPoint() {
        return reorderPoint;
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