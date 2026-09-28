package com.example.erp.ecommerce;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
class PendingOrderExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderExpiryScheduler.class);
    private final StorefrontOrderService orderService;

    PendingOrderExpiryScheduler(StorefrontOrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${app.ecommerce.expiry-scan-interval-ms:60000}")
    void expireUnpaidOrders() {
        for (var orderId : orderService.findDueOrderIds(Instant.now())) {
            try {
                orderService.expirePendingOrder(orderId);
            } catch (OptimisticLockingFailureException conflict) {
                // A payment or customer cancellation won the race; its transaction
                // also owns the reservation update, so the next scan can skip it.
                log.debug("Online order {} changed while expiry was processing", orderId);
            }
        }
    }
}
