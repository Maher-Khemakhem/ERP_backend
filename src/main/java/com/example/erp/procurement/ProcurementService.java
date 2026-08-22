package com.example.erp.procurement;

import com.example.erp.shared.DomainEvent;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
class ProcurementService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final LowStockAlertRepository alertRepository;
    private final ApplicationEventPublisher events;

    ProcurementService(PurchaseOrderRepository purchaseOrderRepository,
                       LowStockAlertRepository alertRepository,
                       ApplicationEventPublisher events) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.alertRepository = alertRepository;
        this.events = events;
    }

    /** Crée un brouillon de commande, initié manuellement par un manager. */
    @Transactional
    public UUID createDraftSuggestion(UUID supplierId, UUID productId, Quantity quantity, Money estimatedUnitCost) {
        PurchaseOrder order = PurchaseOrder.draft(supplierId);
        order.addLine(productId, quantity, estimatedUnitCost);
        purchaseOrderRepository.save(order);
        return order.getId();
    }

    @Transactional
    public void placeOrder(UUID purchaseOrderId) {
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Commande introuvable : " + purchaseOrderId));

        order.place();

        purchaseOrderRepository.save(order);
        publishAndClear(order);
    }

    @Transactional
    public void receiveGoods(UUID purchaseOrderId) {
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Commande introuvable : " + purchaseOrderId));

        order.markReceived();

        purchaseOrderRepository.save(order);
        publishAndClear(order);
    }

    /** Liste des alertes de stock bas non encore traitées, pour l'écran back-office du manager. */
    @Transactional(readOnly = true)
    public List<LowStockAlert> getUnacknowledgedAlerts() {
        return alertRepository.findByAcknowledgedFalse();
    }

    /** Le manager marque une alerte comme traitée (a commandé, ou a décidé de ne rien faire). */
    @Transactional
    public void acknowledgeAlert(UUID alertId) {
        LowStockAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alerte introuvable : " + alertId));
        alert.acknowledge();
        alertRepository.save(alert);
    }

    private void publishAndClear(PurchaseOrder order) {
        List<DomainEvent> pending = order.domainEvents();
        pending.forEach(events::publishEvent);
        order.clearDomainEvents();
    }
}