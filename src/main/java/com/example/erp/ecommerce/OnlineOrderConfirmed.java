package com.example.erp.ecommerce;

import com.example.erp.shared.DomainEvent;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable channel-specific sale snapshot emitted after online stock commit. */
public record OnlineOrderConfirmed(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        UUID customerId,
        String orderNumber,
        String paymentMethod,
        Money netAmount,
        Money vatAmount,
        Money grossAmount,
        Money costOfGoodsSold,
        List<Line> lines) implements DomainEvent {

    public record Line(UUID productId, String productName, Quantity quantity,
                       Money unitPrice, Money lineTotal, Money vatAmount) {
    }

    static OnlineOrderConfirmed of(OnlineOrder order, String paymentMethod, Money costOfGoodsSold) {
        return new OnlineOrderConfirmed(
                UUID.randomUUID(),
                Instant.now(),
                order.getId(),
                order.getCustomerId(),
                order.getOrderNumber(),
                paymentMethod,
                order.getNetAmount(),
                order.getVatAmount(),
                order.getTotalAmount(),
                costOfGoodsSold,
                order.getLines().stream()
                        .map(line -> new Line(line.getProductId(), line.getProductName(), line.getQuantity(),
                                line.getUnitPrice(), line.getLineTotal(), line.getVatAmount()))
                        .toList());
    }
}
