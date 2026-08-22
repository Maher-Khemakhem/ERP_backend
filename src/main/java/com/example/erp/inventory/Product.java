package com.example.erp.inventory;


import com.example.erp.shared.Quantity;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;

/**
 * Product — données de référence décrivant ce qui est vendu,
 * indépendamment de la quantité actuellement en stock (c'est le rôle
 * de StockLevel).
 *
 * Entité, mais PAS un aggregate root à elle seule : Product a une
 * identité et un cycle de vie, mais rien en dehors du module Inventory
 * ne doit la modifier directement, sauf via les services applicatifs
 * du module lui-même.
 */
@Entity
@Table(name = "product", schema = "inventory")
public class Product {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String sku; // code-barres / référence produit

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Quantity.UnitType unitType; // EACH (à l'unité) ou WEIGHT (au poids)

    private boolean perishable; // vrai pour les produits avec date de péremption

    @Column(name = "reorder_point")
    private int reorderPoint; // seuil de réapprovisionnement (en unités entières)

    protected Product() {
        // requis par JPA/Hibernate — ne pas utiliser directement
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

    /**
     * Point d'entrée pour créer un nouveau produit.
     * Un factory method statique plutôt qu'un constructeur public :
     * ça garde la porte de création unique et facile à faire évoluer
     * (ex. ajouter une validation supplémentaire plus tard sans casser
     * les appelants).
     */
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
        return id.equals(other.id); // égalité d'entité = égalité d'identité, pas d'attributs
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}