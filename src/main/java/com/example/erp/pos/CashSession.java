package com.example.erp.pos;

import com.example.erp.shared.Money;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "cash_session", schema = "pos")
public class CashSession {

    @Id
    private UUID id;
    @Version
    private long version;

    @Column(name = "till_id", nullable = false)
    private UUID tillId;

    @Column(name = "cashier_name")
    private String cashierName;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "opening_float"))
    private Money openingFloat;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "expected_cash"))
    private Money expectedCash;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "counted_cash"))
    private Money countedCash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected CashSession() {
    }

    private CashSession(UUID id, UUID tillId, String cashierName, Money openingFloat) {
        this.id = Objects.requireNonNull(id);
        this.tillId = Objects.requireNonNull(tillId);
        this.cashierName = cashierName == null || cashierName.isBlank() ? "Cashier" : cashierName.trim();
        this.openingFloat = Objects.requireNonNull(openingFloat);
        this.expectedCash = openingFloat;
        this.status = SessionStatus.OPEN;
        this.openedAt = Instant.now();
    }

    public static CashSession open(UUID tillId, String cashierName, Money openingFloat) {
        if (openingFloat.isNegative()) {
            throw new IllegalArgumentException("Le fond de caisse ne peut pas être négatif");
        }
        return new CashSession(UUID.randomUUID(), tillId, cashierName, openingFloat);
    }

    public void recordCashPayment(Money amountPaidInCash) {
        requireOpen();
        expectedCash = expectedCash.add(amountPaidInCash);
    }

    public void close(Money actualCountedCash) {
        requireOpen();
        if (actualCountedCash == null || actualCountedCash.isNegative()) {
            throw new IllegalArgumentException("Counted cash cannot be negative");
        }
        this.countedCash = actualCountedCash;
        this.status = SessionStatus.CLOSED;
        this.closedAt = Instant.now();
    }

    private void requireOpen() {
        if (status != SessionStatus.OPEN) {
            throw new IllegalStateException("La session de caisse " + id + " n'est pas ouverte");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTillId() {
        return tillId;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public Money getExpectedCash() {
        return expectedCash;
    }

    public String getCashierName() {
        return cashierName;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Money getCountedCash() {
        return countedCash;
    }

    public Money getOpeningFloat() {
        return openingFloat;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CashSession other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public enum SessionStatus {
        OPEN, CLOSED
    }
}
