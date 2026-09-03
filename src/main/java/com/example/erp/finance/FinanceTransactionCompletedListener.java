package com.example.erp.finance;

import com.example.erp.pos.TransactionCompleted;
import com.example.erp.shared.Money;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/** Posts the balanced financial impact of every completed POS sale. */
@Component
class FinanceTransactionCompletedListener {
    private final FinanceService financeService;
    FinanceTransactionCompletedListener(FinanceService financeService) { this.financeService = financeService; }

    @ApplicationModuleListener
    void on(TransactionCompleted event) {
        AccountCode paymentAccount = "CASH".equalsIgnoreCase(event.paymentMethod())
                ? AccountCode.CASH_ON_HAND : AccountCode.CARD_CLEARING;
        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(paymentAccount, event.grossAmount(), "Customer payment"));
        lines.add(JournalLine.credit(AccountCode.SALES_REVENUE, event.netAmount(), "Retail sale"));
        if (!event.vatAmount().isZero()) lines.add(JournalLine.credit(AccountCode.OUTPUT_VAT, event.vatAmount(), "Output VAT"));
        if (!event.costOfGoodsSold().isZero()) {
            lines.add(JournalLine.debit(AccountCode.COST_OF_GOODS_SOLD, event.costOfGoodsSold(), "Cost of goods sold"));
            lines.add(JournalLine.credit(AccountCode.INVENTORY, event.costOfGoodsSold(), "Inventory consumed"));
        }
        financeService.postIfAbsent("sale:" + event.transactionId(), "POS sale " + event.transactionId(),
                event.transactionId(), lines);
    }
}
