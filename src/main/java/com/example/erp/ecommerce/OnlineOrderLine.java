package com.example.erp.ecommerce;

import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "online_order_line", schema = "ecommerce")
class OnlineOrderLine {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "quantity_value", nullable = false, precision = 19, scale = 3))
    @AttributeOverride(name = "unitType", column = @Column(name = "quantity_unit_type", nullable = false, length = 20))
    private Quantity quantity;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "unit_price", nullable = false, precision = 19, scale = 3))
    private Money unitPrice;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "line_total", nullable = false, precision = 19, scale = 3))
    private Money lineTotal;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "vat_amount", nullable = false, precision = 19, scale = 3))
    private Money vatAmount;

    protected OnlineOrderLine() {
    }

    private OnlineOrderLine(UUID productId, String productName, Quantity quantity,
                            Money unitPrice, Money lineTotal, Money vatAmount) {
        this.id = UUID.randomUUID();
        this.productId = Objects.requireNonNull(productId);
        this.productName = Objects.requireNonNull(productName);
        this.quantity = Objects.requireNonNull(quantity);
        this.unitPrice = Objects.requireNonNull(unitPrice);
        this.lineTotal = Objects.requireNonNull(lineTotal);
        this.vatAmount = Objects.requireNonNull(vatAmount);
    }

    static OnlineOrderLine from(StorefrontCatalogService.LineQuote quote) {
        return new OnlineOrderLine(
                quote.product().id(),
                quote.product().name(),
                quote.quantity(),
                quote.unitPrice(),
                quote.lineTotal(),
                quote.vatAmount());
    }

    UUID getId() { return id; }
    UUID getProductId() { return productId; }
    String getProductName() { return productName; }
    Quantity getQuantity() { return quantity; }
    Money getUnitPrice() { return unitPrice; }
    Money getLineTotal() { return lineTotal; }
    Money getVatAmount() { return vatAmount; }
}
