package com.example.erp.procurement;

import com.example.erp.shared.DomainEvent;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Published after a physical receipt has been recorded. */
public record GoodsReceived(
        UUID eventId,
        Instant occurredAt,
        UUID purchaseOrderId,
        UUID supplierId,
        List<ReceiptLine> lines
) implements DomainEvent {

    public static GoodsReceived of(UUID purchaseOrderId, UUID supplierId,
                                   List<ReceiptLine> lines) {
        return new GoodsReceived(UUID.randomUUID(), Instant.now(), purchaseOrderId, supplierId, lines);
    }

    /** Actual received quantity plus the lot information entered at receiving. */
    public record ReceiptLine(
            UUID purchaseOrderLineId,
            UUID productId,
            Quantity quantity,
            Money unitCost,
            String lotNumber,
            LocalDate expiryDate,
            LocalDate receivedDate,
            String supplierReference
    ) {
    }
}
