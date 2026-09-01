package com.example.erp.procurement;

import com.example.erp.inventory.InventoryService;
import com.example.erp.shared.DomainEvent;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
class ProcurementService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final LowStockAlertRepository alertRepository;
    private final ApplicationEventPublisher events;
    private final InventoryService inventoryService;

    ProcurementService(PurchaseOrderRepository purchaseOrderRepository,
                       LowStockAlertRepository alertRepository,
                       ApplicationEventPublisher events,
                       InventoryService inventoryService) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.alertRepository = alertRepository;
        this.events = events;
        this.inventoryService = inventoryService;
    }

    record OrderLineInput(UUID productId, Quantity quantity, Money estimatedUnitCost) {
    }

    record ReceiptInput(UUID purchaseOrderLineId, Quantity quantity, String lotNumber,
                        LocalDate expiryDate, LocalDate receivedDate, String supplierReference) {
    }

    /** Creates a draft purchase order with all requested lines. */
    @Transactional
    public UUID createDraft(UUID supplierId, List<OrderLineInput> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("A purchase order must contain at least one line");
        }

        PurchaseOrder order = PurchaseOrder.draft(supplierId);
        lines.forEach(line -> order.addLine(line.productId(), line.quantity(), line.estimatedUnitCost()));
        purchaseOrderRepository.save(order);
        return order.getId();
    }

    /** Kept for callers that create a one-line reorder suggestion. */
    @Transactional
    public UUID createDraftSuggestion(UUID supplierId, UUID productId,
                                      Quantity quantity, Money estimatedUnitCost) {
        return createDraft(supplierId, List.of(new OrderLineInput(productId, quantity, estimatedUnitCost)));
    }

    @Transactional
    public void placeOrder(UUID purchaseOrderId) {
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found: " + purchaseOrderId));

        order.place();

        purchaseOrderRepository.save(order);
        publishAndClear(order);
    }

    @Transactional
    public void cancelOrder(UUID purchaseOrderId) {
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found: " + purchaseOrderId));

        order.cancel();
        purchaseOrderRepository.save(order);
        order.clearDomainEvents();
    }

    /**
     * Records a complete receipt and creates one Inventory lot per order line.
     * The operation is atomic: a failed lot creation rolls back the order status
     * and every lot created earlier in this method.
     */
    @Transactional
    public void receiveGoods(UUID purchaseOrderId, List<ReceiptInput> receiptInputs) {
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found: " + purchaseOrderId));

        if (receiptInputs == null || receiptInputs.size() != order.getLines().size()) {
            throw new IllegalArgumentException("A complete receipt is required for every purchase-order line");
        }

        Map<UUID, PurchaseOrderLine> orderLines = order.getLines().stream()
                .collect(Collectors.toMap(PurchaseOrderLine::getId, Function.identity()));
        if (receiptInputs.stream().map(ReceiptInput::purchaseOrderLineId).count()
                != new HashSet<>(receiptInputs.stream().map(ReceiptInput::purchaseOrderLineId).toList()).size()) {
            throw new IllegalArgumentException("A purchase-order line can only be received once");
        }

        List<GoodsReceived.ReceiptLine> receivedLines = receiptInputs.stream().map(receipt -> {
            PurchaseOrderLine orderLine = orderLines.get(receipt.purchaseOrderLineId());
            if (orderLine == null) {
                throw new IllegalArgumentException("Receipt line does not belong to this purchase order");
            }
            if (receipt.quantity().unitType() != orderLine.getQuantity().unitType()
                    || receipt.quantity().value().compareTo(orderLine.getQuantity().value()) != 0) {
                throw new IllegalArgumentException("Received quantity must match the ordered quantity");
            }

            inventoryService.receiveStock(
                    orderLine.getProductId(), receipt.quantity(), receipt.lotNumber(),
                    receipt.expiryDate(), receipt.receivedDate(), receipt.supplierReference());

            return new GoodsReceived.ReceiptLine(
                    orderLine.getId(), orderLine.getProductId(), receipt.quantity(),
                    orderLine.getUnitCost(), receipt.lotNumber(), receipt.expiryDate(),
                    receipt.receivedDate(), receipt.supplierReference());
        }).toList();

        order.markReceived(receivedLines);
        purchaseOrderRepository.save(order);
        publishAndClear(order);
    }

    /** Lists low-stock alerts that have not yet been handled. */
    @Transactional(readOnly = true)
    public List<LowStockAlert> getUnacknowledgedAlerts() {
        return alertRepository.findByAcknowledgedFalse();
    }

    /** Marks a low-stock alert as handled. */
    @Transactional
    public void acknowledgeAlert(UUID alertId) {
        LowStockAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));
        alert.acknowledge();
        alertRepository.save(alert);
    }

    private void publishAndClear(PurchaseOrder order) {
        List<DomainEvent> pending = order.domainEvents();
        pending.forEach(events::publishEvent);
        order.clearDomainEvents();
    }
}
