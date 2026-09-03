package com.example.erp.pos;

import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "transaction_line", schema = "pos")
public class TransactionLine {
    @Id
    private UUID id;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Embedded
    private Quantity quantity;
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "line_total"))
    private Money lineTotal;
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "vat_amount", precision = 19, scale = 3))
    private Money vatAmount;

    protected TransactionLine() {
    }

    private TransactionLine(UUID id, UUID productId, Quantity quantity, Money lineTotal, Money vatAmount) {
        this.id = Objects.requireNonNull(id);
        this.productId = Objects.requireNonNull(productId);
        this.quantity = Objects.requireNonNull(quantity);
        this.lineTotal = Objects.requireNonNull(lineTotal);
        this.vatAmount = Objects.requireNonNull(vatAmount);
    }

    public static TransactionLine of(UUID productId, Quantity quantity, Money calculatedPrice) {
        return of(productId, quantity, calculatedPrice, Money.zero());
    }

    public static TransactionLine of(UUID productId, Quantity quantity, Money calculatedPrice, Money vatAmount) {
        if (calculatedPrice.isNegative()) {
            throw new IllegalArgumentException("Line price cannot be negative");
        }
        if (vatAmount.isNegative() || vatAmount.isGreaterThan(calculatedPrice)) {
            throw new IllegalArgumentException("VAT amount is invalid");
        }
        return new TransactionLine(UUID.randomUUID(), productId, quantity, calculatedPrice, vatAmount);
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public Quantity getQuantity() { return quantity; }
    public Money getLineTotal() { return lineTotal; }
    public Money getVatAmount() { return vatAmount == null ? Money.zero() : vatAmount; }
    public Money getNetTotal() { return lineTotal.subtract(getVatAmount()); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransactionLine other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}
