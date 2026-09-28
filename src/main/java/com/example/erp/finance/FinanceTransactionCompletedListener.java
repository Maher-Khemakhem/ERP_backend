package com.example.erp.finance;

import com.example.erp.pos.TransactionCompleted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Posts the balanced financial impact of every completed POS sale. */
@Component
class FinanceTransactionCompletedListener {
    private final FinanceService financeService;
    FinanceTransactionCompletedListener(FinanceService financeService) { this.financeService = financeService; }

    @ApplicationModuleListener
    void on(TransactionCompleted event) {
        financeService.postSale("sale:" + event.transactionId(), "POS sale " + event.transactionId(),
                event.transactionId(), event.paymentMethod(), event.netAmount(), event.vatAmount(),
                event.grossAmount(), event.costOfGoodsSold());
    }
}
