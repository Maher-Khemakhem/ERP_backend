package com.example.erp.ecommerce;

import com.example.erp.shared.Quantity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "cart", schema = "ecommerce")
class Cart {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, unique = true)
    private UUID customerId;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "cart_id", nullable = false)
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {
    }

    private Cart(UUID customerId) {
        this.id = UUID.randomUUID();
        this.customerId = Objects.requireNonNull(customerId);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    static Cart forCustomer(UUID customerId) {
        return new Cart(customerId);
    }

    void addItem(UUID productId, Quantity quantity) {
        CartItem existing = items.stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst()
                .orElse(null);
        if (existing == null) {
            items.add(CartItem.create(productId, quantity));
        } else {
            existing.increase(quantity);
        }
        touch();
    }

    void replaceItem(UUID productId, Quantity quantity) {
        CartItem item = item(productId);
        item.replace(quantity);
        touch();
    }

    void removeItem(UUID productId) {
        boolean removed = items.removeIf(item -> item.getProductId().equals(productId));
        if (!removed) {
            throw new IllegalArgumentException("Cart item not found: " + productId);
        }
        touch();
    }

    void clear() {
        items.clear();
        touch();
    }

    UUID getCustomerId() {
        return customerId;
    }

    List<CartItem> getItems() {
        return List.copyOf(items);
    }

    private CartItem item(UUID productId) {
        return items.stream()
                .filter(candidate -> candidate.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found: " + productId));
    }

    private void touch() {
        updatedAt = Instant.now();
    }
}
