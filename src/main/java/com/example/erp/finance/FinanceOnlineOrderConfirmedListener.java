package com.example.erp.finance;

import com.example.erp.ecommerce.OnlineOrderConfirmed;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Posts the balanced financial impact of a confirmed online sale. */
@Component
class FinanceOnlineOrderConfirmedListener {

    private final FinanceService financeService;

    FinanceOnlineOrderConfirmedListener(FinanceService financeService) {
        this.financeService = financeService;
    }

    @ApplicationModuleListener
    void on(OnlineOrderConfirmed event) {
        financeService.postSale(
                "online-sale:" + event.orderId(),
                "Online sale " + event.orderNumber(),
                event.orderId(),
                event.paymentMethod(),
                event.netAmount(),
                event.vatAmount(),
                event.grossAmount(),
                event.costOfGoodsSold());
    }
}
