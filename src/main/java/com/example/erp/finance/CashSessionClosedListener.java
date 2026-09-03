package com.example.erp.finance;

import com.example.erp.pos.CashSessionClosed;
import com.example.erp.shared.Money;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/** Settles a cash drawer into the bank and records over/short differences. */
@Component
class CashSessionClosedListener {
    private final FinanceService financeService;
    CashSessionClosedListener(FinanceService financeService) { this.financeService = financeService; }

    @ApplicationModuleListener
    void on(CashSessionClosed event) {
        if (event.expectedCash().isZero() && event.countedCash().isZero()) return;
        Money variance = event.countedCash().subtract(event.expectedCash());
        List<JournalLine> lines = new ArrayList<>();
        if (!event.countedCash().isZero()) lines.add(JournalLine.debit(AccountCode.BANK, event.countedCash(), "Cash deposited"));
        if (!event.expectedCash().isZero()) lines.add(JournalLine.credit(AccountCode.CASH_ON_HAND, event.expectedCash(), "Close cash drawer"));
        if (variance.isGreaterThan(Money.zero())) {
            lines.add(JournalLine.credit(AccountCode.CASH_VARIANCE, variance, "Cash overage"));
        } else if (!variance.isZero()) {
            lines.add(JournalLine.debit(AccountCode.CASH_VARIANCE,
                    variance.multiply(java.math.BigDecimal.valueOf(-1)), "Cash shortage"));
        }
        financeService.postIfAbsent("cash-close:" + event.cashSessionId(), "Cash session close " + event.cashSessionId(),
                event.cashSessionId(), lines);
    }
}
