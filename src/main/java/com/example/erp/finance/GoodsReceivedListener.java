package com.example.erp.finance;

import com.example.erp.procurement.GoodsReceived;
import com.example.erp.shared.Money;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import java.util.List;

/** Posts inventory and GRNI when goods physically arrive. */
@Component
class GoodsReceivedListener {
    private final FinanceService financeService;
    GoodsReceivedListener(FinanceService financeService) { this.financeService = financeService; }

    @ApplicationModuleListener
    void on(GoodsReceived event) {
        Money total = event.lines().stream()
                .map(line -> line.unitCost().multiply(line.quantity().value()))
                .reduce(Money.zero(), Money::add);
        if (total.isZero()) return;
        financeService.postIfAbsent("goods-received:" + event.purchaseOrderId(),
                "Goods received for purchase order " + event.purchaseOrderId(), event.purchaseOrderId(), List.of(
                        JournalLine.debit(AccountCode.INVENTORY, total, "Inventory received"),
                        JournalLine.credit(AccountCode.GOODS_RECEIVED_NOT_INVOICED, total, "Uninvoiced receipt")));
    }
}
