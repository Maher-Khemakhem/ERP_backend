package com.example.erp.inventory;


import com.example.erp.shared.Money;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Lot — un lot précis d'un produit reçu à une date donnée, avec sa
 * propre date de péremption (si le produit est périssable).
 *
 * C'est ce qui permet le FEFO (First-Expire-First-Out) : deux lots du
 * même Product peuvent avoir des dates de péremption différentes, et
 * la vente doit toujours consommer le lot qui expire en premier.
 *
 * Entité (a une identité), mais accessible uniquement via l'aggregate
 * root StockLevel qui la contient.
 */
@Entity
@Table(name = "lot", schema = "inventory")
public class Lot {

    @Id
    private UUID id;

    @Column(name = "lot_number", nullable = false)
    private String lotNumber;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "expiry_date")
    private LocalDate expiryDate; // null si le produit n'est pas périssable

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "supplier_reference")
    private String supplierReference;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "unit_cost", precision = 19, scale = 3))
    private Money unitCost;

    protected Lot() {
        // requis par JPA/Hibernate
    }

    private Lot(UUID id, String lotNumber, UUID productId,
                LocalDate expiryDate, LocalDate receivedDate, String supplierReference, Money unitCost) {
        this.id = Objects.requireNonNull(id);
        this.lotNumber = requireNonBlank(lotNumber, "lotNumber");
        this.productId = Objects.requireNonNull(productId, "productId");
        this.expiryDate = expiryDate;
        this.receivedDate = Objects.requireNonNull(receivedDate, "receivedDate");
        this.supplierReference = supplierReference;
        this.unitCost = Objects.requireNonNull(unitCost);
    }

    /**
     * Crée un nouveau lot au moment de la réception marchandise
     * (GoodsReceipt, module Procurement). C'est ici que naît
     * l'information "date de péremption" dans le système.
     */
    public static Lot receive(String lotNumber, UUID productId, LocalDate expiryDate,
                              LocalDate receivedDate, String supplierReference) {
        return receive(lotNumber, productId, expiryDate, receivedDate, supplierReference, Money.zero());
    }

    public static Lot receive(String lotNumber, UUID productId, LocalDate expiryDate,
                              LocalDate receivedDate, String supplierReference, Money unitCost) {
        if (unitCost.isNegative()) {
            throw new IllegalArgumentException("Lot unit cost cannot be negative");
        }
        return new Lot(UUID.randomUUID(), lotNumber, productId,
                expiryDate, receivedDate, supplierReference, unitCost);
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " ne peut pas être vide");
        }
        return value;
    }

    /**
     * Nombre de jours avant péremption, à partir d'aujourd'hui.
     * Retourne un nombre très grand si le produit n'est pas périssable
     * (pas de date de péremption), pour que le tri FEFO le place
     * naturellement en dernier.
     */
    public long daysUntilExpiry(LocalDate today) {
        if (expiryDate == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(today, expiryDate);
    }

    public boolean isExpired(LocalDate today) {
        return expiryDate != null && expiryDate.isBefore(today);
    }

    public boolean isPerishable() {
        return expiryDate != null;
    }

    public UUID getId() {
        return id;
    }

    public String getLotNumber() {
        return lotNumber;
    }

    public UUID getProductId() {
        return productId;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public LocalDate getReceivedDate() {
        return receivedDate;
    }

    public Money getUnitCost() {
        return unitCost == null ? Money.zero() : unitCost;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Lot other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
