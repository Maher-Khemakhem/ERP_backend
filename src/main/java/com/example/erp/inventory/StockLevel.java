package com.example.erp.inventory;

import com.example.erp.shared.DomainEvent;
import com.example.erp.shared.Quantity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * StockLevel — aggregate root du module Inventory.
 *
 * Représente la position de stock COMPLÈTE d'un Product : la somme de
 * tous ses lots, avec leurs quantités disponibles et réservées.
 *
 * C'est le SEUL point d'entrée pour modifier le stock. Personne, ni à
 * l'intérieur ni à l'extérieur du module, ne doit manipuler un lot
 * directement — tout passe par receive(), sell(), reserve(), writeOff().
 */
@Entity
@Table(name = "stock_level", schema = "inventory")
public class StockLevel {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false, unique = true)
    private UUID productId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "stock_level_id")
    private List<LotEntry> lots = new ArrayList<>();

    @Transient
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected StockLevel() {
        // requis par JPA/Hibernate
    }

    private StockLevel(UUID id, UUID productId) {
        this.id = Objects.requireNonNull(id);
        this.productId = Objects.requireNonNull(productId);
    }

    public static StockLevel initialize(UUID productId) {
        return new StockLevel(UUID.randomUUID(), productId);
    }

    /** Réception d'un nouveau lot (venant du module Procurement via GoodsReceived). */
    public void receive(Lot lot, Quantity quantity) {
        lots.add(new LotEntry(lot, quantity, Quantity.zero(quantity.unitType())));
        domainEvents.add(StockReceived.of(productId, lot.getId(), quantity));
    }

    /**
     * Vend une quantité en suivant la règle FEFO (First-Expire-First-Out).
     * Peut puiser dans plusieurs lots si un seul ne suffit pas.
     *
     * @throws InsufficientStockException si la quantité disponible totale
     *         est inférieure à la quantité demandée
     */
    public void sell(Quantity quantityToSell, LocalDate today, int reorderPoint) {
        Quantity available = totalAvailable(quantityToSell.unitType());
        if (available.isGreaterThanOrEqualTo(quantityToSell)) {
            deductFefo(quantityToSell, today);
            domainEvents.add(StockSold.of(productId, quantityToSell));
            checkReorderPoint(reorderPoint);
        } else {
            throw new InsufficientStockException(productId, quantityToSell, available);
        }
    }

    /** Réserve du stock sans le déduire définitivement. */
    public void reserve(Quantity quantity) {
        reserve(quantity, today());
    }

    public void reserve(Quantity quantity, LocalDate today) {
        Quantity available = totalAvailable(quantity.unitType());
        if (!available.isGreaterThanOrEqualTo(quantity)) {
            throw new InsufficientStockException(productId, quantity, available);
        }
        Quantity remaining = quantity;
        for (LotEntry entry : sortedByExpiry(today)) {
            if (remaining.isZero()) break;
            remaining = entry.reserve(remaining);
        }
    }

    /** Sortie de stock pour cause de péremption ou casse — jamais une vente normale. */
    public void writeOff(UUID lotId, Quantity quantity, String reason, int reorderPoint) {
        LotEntry entry = findLotEntry(lotId);
        entry.deduct(quantity);
        domainEvents.add(StockWrittenOff.of(productId, lotId, quantity, reason));
        checkReorderPoint(reorderPoint);
    }

    public List<DomainEvent> domainEvents() {
        return List.copyOf(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    public UUID getProductId() {
        return productId;
    }

    public List<LotEntry> getLots() {
        return List.copyOf(lots);
    }

    private LocalDate today() {
        return LocalDate.now();
    }

    private void deductFefo(Quantity quantityToSell, LocalDate today) {
        Quantity remaining = quantityToSell;
        for (LotEntry entry : sortedByExpiry(today)) {
            if (remaining.isZero()) break;
            remaining = entry.deductUpTo(remaining);
        }
    }

    private List<LotEntry> sortedByExpiry(LocalDate today) {
        return lots.stream()
                .filter(e -> !e.onHand().isZero())
                .sorted((a, b) -> Long.compare(
                        a.lot().daysUntilExpiry(today),
                        b.lot().daysUntilExpiry(today)))
                .collect(Collectors.toList());
    }

    private Quantity totalAvailable(Quantity.UnitType unitType) {
        Quantity total = Quantity.zero(unitType);
        for (LotEntry entry : lots) {
            total = total.add(entry.available());
        }
        return total;
    }

    private LotEntry findLotEntry(UUID lotId) {
        return lots.stream()
                .filter(e -> e.lot().getId().equals(lotId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Lot introuvable : " + lotId));
    }

    /**
     * Compare le stock disponible total au seuil de réapprovisionnement.
     * Publie LowStockThresholdReached si on est passé EN DESSOUS du seuil.
     * L'événement ne fait que signaler le fait — aucune action
     * automatique n'est déclenchée en aval (voir LowStockAlert côté
     * Procurement : une simple notification, pas une commande auto).
     */
    private void checkReorderPoint(int reorderPoint) {
        if (lots.isEmpty()) {
            domainEvents.add(LowStockThresholdReached.of(productId));
            return;
        }
        Quantity.UnitType unitType = lots.get(0).onHand().unitType();
        Quantity available = totalAvailable(unitType);
        Quantity threshold = unitType == Quantity.UnitType.EACH
                ? Quantity.each(reorderPoint)
                : Quantity.kg(reorderPoint);

        if (!available.isGreaterThan(threshold)) {
            domainEvents.add(LowStockThresholdReached.of(productId));
        }
    }

    public static final class InsufficientStockException extends RuntimeException {
        public InsufficientStockException(UUID productId, Quantity requested, Quantity available) {
            super("Stock insuffisant pour le produit " + productId
                    + " : demandé " + requested + ", disponible " + available);
        }
    }
}