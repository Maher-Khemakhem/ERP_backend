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
    @Version
    private long version;
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
        return addLine(productId, quantity, calculatedPrice, Money.zero());
    }

    public UUID addLine(UUID productId, Quantity quantity, Money calculatedPrice, Money vatAmount) {
        requireInProgress();
        TransactionLine line = TransactionLine.of(productId, quantity, calculatedPrice, vatAmount);
        lines.add(line);
        return line.getId();
    }

    public void removeLine(UUID lineId) {
        requireInProgress();
        boolean removed = lines.removeIf(line -> line.getId().equals(lineId));
        if (!removed) throw new IllegalArgumentException("Line not found: " + lineId);
    }

    public void complete(PaymentMethod paymentMethod) {
        requireInProgress();
        if (lines.isEmpty()) throw new IllegalStateException("Cannot complete an empty ticket");
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
        for (TransactionLine line : lines) total = total.add(line.getLineTotal());
        return total;
    }

    private Money calculateSubtotal() {
        Money subtotal = Money.zero();
        for (TransactionLine line : lines) subtotal = subtotal.add(line.getNetTotal());
        return subtotal;
    }

    private void requireInProgress() {
        if (status != TransactionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Ticket is no longer editable: " + status);
        }
    }

    public UUID getId() { return id; }
    public UUID getCashSessionId() { return cashSessionId; }
    public TransactionStatus getStatus() { return status; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    /** Total includes per-line VAT amounts stored at the time of line creation. */
    public Money getTotal() { return calculateTotal(); }
    /** Sum of line-level VAT amounts — each line carries the VAT computed from its product's catalog rate. */
    public Money getVatTotal() {
        Money vat = Money.zero();
        for (TransactionLine line : lines) vat = vat.add(line.getVatAmount());
        return vat;
    }
    /** Net total = gross total minus per-line VAT. */
    public Money getNetTotal() { return calculateSubtotal(); }
    public List<TransactionLine> getLines() { return List.copyOf(lines); }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PosTransaction other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    public enum TransactionStatus { IN_PROGRESS, COMPLETED, VOIDED }
    // STRIPE remains readable for tickets created before POS Checkout was retired.
    public enum PaymentMethod { CASH, CARD, STRIPE }
}
