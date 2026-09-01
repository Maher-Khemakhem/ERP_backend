package com.example.erp.pos;

import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "pos_transaction", schema = "pos")
public class PosTransaction {

    @Id
    private UUID id;

    @Column(name = "cash_session_id", nullable = false)
    private UUID cashSessionId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "transaction_id")
    private List<TransactionLine> lines = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected PosTransaction() {
    }

    private PosTransaction(UUID id, UUID cashSessionId) {
        this.id = Objects.requireNonNull(id);
        this.cashSessionId = Objects.requireNonNull(cashSessionId);
        this.status = TransactionStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
    }

    public static PosTransaction start(UUID cashSessionId) {
        return new PosTransaction(UUID.randomUUID(), cashSessionId);
    }

    public UUID addLine(UUID productId, Quantity quantity, Money calculatedPrice) {
        requireInProgress();
        TransactionLine line = TransactionLine.of(productId, quantity, calculatedPrice);
        lines.add(line);
        return line.getId();
    }

    public void removeLine(UUID lineId) {
        requireInProgress();
        boolean removed = lines.removeIf(line -> line.getId().equals(lineId));
        if (!removed) {
            throw new IllegalArgumentException("Ligne introuvable : " + lineId);
        }
    }

    public void complete(PaymentMethod paymentMethod) {
        requireInProgress();
        if (lines.isEmpty()) {
            throw new IllegalStateException("Impossible de finaliser un ticket vide");
        }
        this.paymentMethod = Objects.requireNonNull(paymentMethod);
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void voidTransaction(String reason) {
        requireInProgress();
        this.status = TransactionStatus.VOIDED;
    }

    private Money calculateTotal() {
        Money total = Money.zero();
        for (TransactionLine line : lines) {
            total = total.add(line.getLineTotal());
        }
        return total;
    }

    private void requireInProgress() {
        if (status != TransactionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Le ticket " + id + " n'est plus modifiable (statut : " + status + ")");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCashSessionId() {
        return cashSessionId;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public Money getTotal() {
        return calculateTotal();
    }

    public List<TransactionLine> getLines() {
        return List.copyOf(lines);
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PosTransaction other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public enum TransactionStatus {
        IN_PROGRESS, COMPLETED, VOIDED
    }

    public enum PaymentMethod {
        CASH, CARD
    }
}
