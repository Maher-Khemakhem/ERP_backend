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

    protected TransactionLine() {
    }

    private TransactionLine(UUID id, UUID productId, Quantity quantity, Money lineTotal) {
        this.id = Objects.requireNonNull(id);
        this.productId = Objects.requireNonNull(productId);
        this.quantity = Objects.requireNonNull(quantity);
        this.lineTotal = Objects.requireNonNull(lineTotal);
    }

    public static TransactionLine of(UUID productId, Quantity quantity, Money calculatedPrice) {
        if (calculatedPrice.isNegative()) {
            throw new IllegalArgumentException("Le prix d'une ligne ne peut pas être négatif");
        }
        return new TransactionLine(UUID.randomUUID(), productId, quantity, calculatedPrice);
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public Quantity getQuantity() {
        return quantity;
    }

    public Money getLineTotal() {
        return lineTotal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransactionLine other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
