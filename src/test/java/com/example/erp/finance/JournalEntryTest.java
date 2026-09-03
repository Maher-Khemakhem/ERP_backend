package com.example.erp.finance;

import com.example.erp.shared.Money;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class JournalEntryTest {
    @Test
    void balancedEntryContainsEqualDebitsAndCredits() {
        JournalEntry entry = JournalEntry.post("sale:1", "Sale", UUID.randomUUID(), List.of(
                JournalLine.debit(AccountCode.CASH_ON_HAND, Money.of(119), "Payment"),
                JournalLine.credit(AccountCode.SALES_REVENUE, Money.of(100), "Net sale"),
                JournalLine.credit(AccountCode.OUTPUT_VAT, Money.of(19), "VAT")));

        assertThat(entry.getLines()).hasSize(3);
    }

    @Test
    void unbalancedEntryIsRejected() {
        assertThatThrownBy(() -> JournalEntry.post("bad:1", "Bad", UUID.randomUUID(), List.of(
                JournalLine.debit(AccountCode.CASH_ON_HAND, Money.of(10), "Payment"),
                JournalLine.credit(AccountCode.SALES_REVENUE, Money.of(9), "Sale"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not balanced");
    }

    @Test
    void duplicateEventKeyDoesNotCreateAnotherJournal() {
        JournalEntryRepository repository = mock(JournalEntryRepository.class);
        SupplierInvoiceRepository invoices = mock(SupplierInvoiceRepository.class);
        SupplierPaymentRepository payments = mock(SupplierPaymentRepository.class);
        JournalEntry existing = JournalEntry.post("sale:1", "Sale", UUID.randomUUID(), List.of(
                JournalLine.debit(AccountCode.CASH_ON_HAND, Money.of(1), "Payment"),
                JournalLine.credit(AccountCode.SALES_REVENUE, Money.of(1), "Sale")));
        when(repository.findByEventKey("sale:1")).thenReturn(java.util.Optional.of(existing));

        FinanceService service = new FinanceService(repository, invoices, payments);
        assertThat(service.postIfAbsent("sale:1", "Sale", existing.getReferenceId(), existing.getLines()))
                .isSameAs(existing);
        verify(repository, never()).save(any());
    }
}
