package com.example.erp.inventory;


import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;

/**
 * LotEntry — associe un Lot à sa quantité en stock (onHand) et sa
 * quantité réservée (reserved), à l'intérieur de l'aggregate StockLevel.
 *
 * Ce n'est PAS un aggregate root : il n'existe et n'est modifié que via
 * StockLevel, jamais directement de l'extérieur. C'est ici que vit
 * l'arithmétique réelle de déduction/réservation pour un lot donné.
 */
@Entity
@Table(name = "lot_entry", schema = "inventory")
class LotEntry {

    @Id
    private UUID id;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "on_hand_value"))
    @AttributeOverride(name = "unitType", column = @Column(name = "on_hand_unit_type"))
    private Quantity onHand;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "reserved_value"))
    @AttributeOverride(name = "unitType", column = @Column(name = "reserved_unit_type"))
    private Quantity reserved;

    protected LotEntry() {
        // requis par JPA/Hibernate
    }

    LotEntry(Lot lot, Quantity onHand, Quantity reserved) {
        this.id = UUID.randomUUID();
        this.lot = Objects.requireNonNull(lot);
        this.onHand = Objects.requireNonNull(onHand);
        this.reserved = Objects.requireNonNull(reserved);
    }

    /** Quantité réellement disponible à la vente = en stock moins réservé. */
    Quantity available() {
        return onHand.subtract(reserved);
    }

    /**
     * Déduit jusqu'à `requested` de ce lot, en respectant ce qui est
     * réellement disponible. Retourne ce qu'il RESTE à déduire ailleurs
     * si ce lot seul ne suffit pas (permet à StockLevel de piocher sur
     * plusieurs lots successifs pour une seule vente FEFO).
     */
    Quantity deductUpTo(Quantity requested) {
        Quantity deductible = available().isGreaterThan(requested) ? requested : available();
        onHand = onHand.subtract(deductible);
        return requested.subtract(deductible);
    }

    /** Deducts quantity that was already reserved for an online order. */
    Quantity deductReservedUpTo(Quantity requested) {
        Quantity deductible = reserved.isGreaterThan(requested) ? requested : reserved;
        onHand = onHand.subtract(deductible);
        reserved = reserved.subtract(deductible);
        return requested.subtract(deductible);
    }

    /** Releases quantity held for an unpaid online order without changing on-hand stock. */
    Quantity releaseReservationUpTo(Quantity requested) {
        Quantity released = reserved.isGreaterThan(requested) ? requested : reserved;
        reserved = reserved.subtract(released);
        return requested.subtract(released);
    }

    Money costFor(Quantity quantity) {
        return lot.getUnitCost().multiply(quantity.value());
    }

    /** Déduction exacte (écart de casse/péremption) — échoue si le lot n'a pas assez. */
    void deduct(Quantity quantity) {
        if (!available().isGreaterThanOrEqualTo(quantity)) {
            throw new IllegalStateException(
                    "Quantité insuffisante dans le lot " + lot.getLotNumber()
                            + " pour déduire " + quantity);
        }
        onHand = onHand.subtract(quantity);
    }

    /** Réserve jusqu'à `requested` sur ce lot ; retourne ce qui reste à réserver ailleurs. */
    Quantity reserve(Quantity requested) {
        Quantity reservable = available().isGreaterThan(requested) ? requested : available();
        reserved = reserved.add(reservable);
        return requested.subtract(reservable);
    }

    Lot lot() {
        return lot;
    }

    Quantity onHand() {
        return onHand;
    }

    Quantity reserved() {
        return reserved;
    }
}
