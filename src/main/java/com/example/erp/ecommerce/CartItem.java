package com.example.erp.ecommerce;

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
@Table(name = "cart_item", schema = "ecommerce")
class CartItem {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "quantity_value", nullable = false, precision = 19, scale = 3))
    @AttributeOverride(name = "unitType", column = @Column(name = "quantity_unit_type", nullable = false, length = 20))
    private Quantity quantity;

    protected CartItem() {
    }

    private CartItem(UUID productId, Quantity quantity) {
        this.id = UUID.randomUUID();
        this.productId = Objects.requireNonNull(productId);
        this.quantity = Objects.requireNonNull(quantity);
    }

    static CartItem create(UUID productId, Quantity quantity) {
        return new CartItem(productId, quantity);
    }

    void increase(Quantity additional) {
        quantity = quantity.add(additional);
    }

    void replace(Quantity replacement) {
        if (quantity.unitType() != replacement.unitType()) {
            throw new IllegalArgumentException("Cart item unit type cannot change");
        }
        quantity = replacement;
    }

    UUID getProductId() {
        return productId;
    }

    Quantity getQuantity() {
        return quantity;
    }
}
