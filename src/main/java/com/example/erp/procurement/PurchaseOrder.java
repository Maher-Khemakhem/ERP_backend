package com.example.erp.procurement;

import com.example.erp.shared.DomainEvent;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PurchaseOrder — aggregate root représentant une commande passée à
 * un fournisseur : des lignes (produit + quantité + prix d'achat),
 * qui passe par des statuts jusqu'à la réception marchandise.
 *
 * C'est PurchaseOrderLine et non TransactionLine qui vit ici — bien
 * que conceptuellement similaires (produit + quantité + prix), ce
 * sont deux entités séparées dans deux modules différents. On aurait
 * pu être tenté de les fusionner ou de les faire hériter d'une classe
 * commune ; on ne le fait pas, car POS et Procurement n'ont aucune
 * raison de partager un type concret — seule la coïncidence de
 * structure les rapproche, pas leur sens métier.
 */
@Entity
@Table(name = "purchase_order", schema = "procurement")
public class PurchaseOrder {

    @Id
    private UUID id;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "purchase_order_id")
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseOrderStatus status;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Transient
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected PurchaseOrder() {
        // requis par JPA/Hibernate
    }

    private PurchaseOrder(UUID id, UUID supplierId) {
        this.id = Objects.requireNonNull(id);
        this.supplierId = Objects.requireNonNull(supplierId);
        this.status = PurchaseOrderStatus.DRAFT;
        this.placedAt = Instant.now();
    }

    public static PurchaseOrder draft(UUID supplierId) {
        return new PurchaseOrder(UUID.randomUUID(), supplierId);
    }

    public void addLine(UUID productId, Quantity quantity, Money unitCost) {
        requireStatus(PurchaseOrderStatus.DRAFT);
        lines.add(PurchaseOrderLine.of(productId, quantity, unitCost));
    }

    /** Envoie la commande au fournisseur — fige les lignes, publie l'événement. */
    public void place() {
        requireStatus(PurchaseOrderStatus.DRAFT);
        if (lines.isEmpty()) {
            throw new IllegalStateException("Impossible d'envoyer une commande sans lignes");
        }
        this.status = PurchaseOrderStatus.PLACED;
        domainEvents.add(PurchaseOrderPlaced.of(id, supplierId, lineSnapshots()));
    }

    /** Cancels an order that has not been received yet. */
    public void cancel() {
        if (status != PurchaseOrderStatus.DRAFT && status != PurchaseOrderStatus.PLACED) {
            throw new IllegalStateException(
                    "Commande " + id + " : seule une commande DRAFT ou PLACED peut être annulée");
        }
        this.status = PurchaseOrderStatus.CANCELLED;
        domainEvents.clear();
    }

    /**
     * Marque la commande comme reçue. Ne crée PAS elle-même les Lot
     * dans Inventory — publie GoodsReceived, qu'Inventory écoutera
     * pour créer ses propres lots avec dates de péremption.
     */
    public void markReceived(List<GoodsReceived.ReceiptLine> receiptLines) {
        requireStatus(PurchaseOrderStatus.PLACED);
        if (receiptLines == null || receiptLines.isEmpty()) {
            throw new IllegalArgumentException("A receipt must contain at least one line");
        }
        this.status = PurchaseOrderStatus.RECEIVED;
        domainEvents.add(GoodsReceived.of(id, supplierId, receiptLines));
    }

    private List<PurchaseOrderPlaced.LineItem> lineSnapshots() {
        return lines.stream()
                .map(l -> new PurchaseOrderPlaced.LineItem(l.getProductId(), l.getQuantity(), l.getUnitCost()))
                .toList();
    }

    private void requireStatus(PurchaseOrderStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Commande " + id + " : statut attendu " + expected + ", statut actuel " + status);
        }
    }

    public UUID getId() {
        return id;
    }

    public List<PurchaseOrderLine> getLines() {
        return List.copyOf(lines);
    }

    public UUID getSupplierId() {
        return supplierId;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public List<DomainEvent> domainEvents() {
        return List.copyOf(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PurchaseOrder other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public enum PurchaseOrderStatus {
        DRAFT, PLACED, RECEIVED, CANCELLED
    }
}
