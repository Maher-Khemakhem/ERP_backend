package com.example.erp.procurement;

import com.example.erp.inventory.LowStockThresholdReached;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Enregistre une alerte de stock bas — pas d'action automatique.
 * Le manager consulte la liste des alertes au back-office et décide
 * lui-même s'il faut commander, auprès de qui, et combien.
 */
@Component
class LowStockThresholdReachedListener {

    private final LowStockAlertRepository alertRepository;

    LowStockThresholdReachedListener(LowStockAlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @ApplicationModuleListener
    void on(LowStockThresholdReached event) {
        alertRepository.save(LowStockAlert.raise(event.productId()));
    }
}