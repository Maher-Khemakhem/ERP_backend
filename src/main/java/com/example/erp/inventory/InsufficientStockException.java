package com.example.erp.inventory;

import com.example.erp.shared.Quantity;
import java.util.UUID;

public class InsufficientStockException extends IllegalStateException {

    private final UUID productId;
    private final Quantity requested;
    private final Quantity available;

    public InsufficientStockException(UUID productId, Quantity requested, Quantity available) {
        super("Insufficient stock for product " + productId
                + ": requested " + requested
                + ", available " + available);
        this.productId = productId;
        this.requested = requested;
        this.available = available;
    }

    public UUID getProductId() {
        return productId;
    }

    public Quantity getRequested() {
        return requested;
    }

    public Quantity getAvailable() {
        return available;
    }
}
