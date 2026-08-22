package com.example.erp.finance;

import com.example.erp.shared.Money;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * LedgerEntry — une écriture comptable simple : un montant, un type
 * (débit/crédit conceptuel via EntryType), une origine, un instant.
 *
 * Volontairement un registre "append-only" minimal — pas un vrai
 * système de comptabilité en partie double pour l'instant. Finance
 * est ici surtout un CONSOMMATEUR d'événements (TransactionCompleted,
 * GoodsReceived, CashSessionClosed) qui enregistre leur impact
 * financier, pas un module de comptabilité complet.
 */
@Entity
@Table(name = "ledger_entry", schema = "finance")
public class LedgerEntry {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryType type;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "amount"))
    private Money amount;

    @Column(nullable = false)
    private String description;

    @Column(name = "reference_id")
    private UUID referenceId; // ex. transactionId, purchaseOrderId, cashSessionId

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected LedgerEntry() {
        // requis par JPA/Hibernate
    }

    private LedgerEntry(UUID id, EntryType type, Money amount, String description, UUID referenceId) {
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
        this.amount = Objects.requireNonNull(amount);
        this.description = requireNonBlank(description, "description");
        this.referenceId = referenceId;
        this.recordedAt = Instant.now();
    }

    public static LedgerEntry revenue(Money amount, String description, UUID referenceId) {
        return new LedgerEntry(UUID.randomUUID(), EntryType.REVENUE, amount, description, referenceId);
    }

    public static LedgerEntry accountsPayable(Money amount, String description, UUID referenceId) {
        return new LedgerEntry(UUID.randomUUID(), EntryType.ACCOUNTS_PAYABLE, amount, description, referenceId);
    }

    public static LedgerEntry cashVariance(Money amount, String description, UUID referenceId) {
        return new LedgerEntry(UUID.randomUUID(), EntryType.CASH_VARIANCE, amount, description, referenceId);
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

    public EntryType getType() {
        return type;
    }

    public Money getAmount() {
        return amount;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LedgerEntry other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public enum EntryType {
        REVENUE, ACCOUNTS_PAYABLE, CASH_VARIANCE
    }
}