package com.example.erp.finance;

import com.example.erp.shared.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
class FinanceService {
    private final JournalEntryRepository journalRepository;
    private final SupplierInvoiceRepository invoiceRepository;
    private final SupplierPaymentRepository paymentRepository;

    FinanceService(JournalEntryRepository journalRepository, SupplierInvoiceRepository invoiceRepository,
                   SupplierPaymentRepository paymentRepository) {
        this.journalRepository = journalRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    JournalEntry postIfAbsent(String eventKey, String description, UUID referenceId, List<JournalLine> lines) {
        return journalRepository.findByEventKey(eventKey)
                .orElseGet(() -> journalRepository.save(JournalEntry.post(eventKey, description, referenceId, lines)));
    }

    @Transactional
    SupplierInvoice createInvoice(UUID supplierId, UUID purchaseOrderId, String invoiceNumber,
                                  Money subtotal, BigDecimal vatRate, LocalDate issueDate, LocalDate dueDate) {
        if (vatRate.signum() < 0 || vatRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("VAT rate must be between 0 and 100");
        }
        if (invoiceRepository.existsByInvoiceNumber(invoiceNumber)) {
            throw new IllegalArgumentException("Invoice number already exists: " + invoiceNumber);
        }
        Money tax = subtotal.multiply(vatRate.divide(BigDecimal.valueOf(100), 8, java.math.RoundingMode.HALF_UP));
        Money total = subtotal.add(tax);
        SupplierInvoice invoice = SupplierInvoice.create(supplierId, purchaseOrderId, invoiceNumber,
                subtotal, tax, total, issueDate, dueDate);
        invoiceRepository.save(invoice);
        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(AccountCode.GOODS_RECEIVED_NOT_INVOICED, subtotal, "Invoice net amount"));
        if (!tax.isZero()) lines.add(JournalLine.debit(AccountCode.INPUT_VAT, tax, "Input VAT"));
        lines.add(JournalLine.credit(AccountCode.ACCOUNTS_PAYABLE, total, "Supplier payable"));
        postIfAbsent("supplier-invoice:" + invoice.getId(), "Supplier invoice " + invoiceNumber,
                invoice.getId(), lines);
        return invoice;
    }

    @Transactional
    SupplierInvoice payInvoice(UUID invoiceId, String paymentReference, Money amount,
                               String paymentMethod, LocalDate paidDate) {
        var existingPayment = paymentRepository.findByPaymentReference(paymentReference);
        if (existingPayment.isPresent()) {
            if (!existingPayment.get().getInvoiceId().equals(invoiceId)) {
                throw new IllegalArgumentException("Payment reference already belongs to another invoice");
            }
            return invoiceRepository.findById(invoiceId).orElseThrow();
        }
        SupplierInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier invoice not found: " + invoiceId));
        invoice.recordPayment(amount);
        paymentRepository.save(SupplierPayment.create(paymentReference, invoiceId, amount, paymentMethod, paidDate));
        AccountCode source = "CASH".equalsIgnoreCase(paymentMethod) ? AccountCode.CASH_ON_HAND : AccountCode.BANK;
        postIfAbsent("supplier-payment:" + paymentReference, "Payment for supplier invoice " + invoice.getInvoiceNumber(),
                invoiceId, List.of(JournalLine.debit(AccountCode.ACCOUNTS_PAYABLE, amount, "Reduce supplier payable"),
                        JournalLine.credit(source, amount, "Supplier payment")));
        return invoiceRepository.save(invoice);
    }

    @Transactional
    void settleCard(String settlementReference, Money amount, LocalDate settlementDate) {
        postIfAbsent("card-settlement:" + settlementReference, "Card settlement " + settlementReference, null,
                List.of(JournalLine.debit(AccountCode.BANK, amount, "Card settlement received"),
                        JournalLine.credit(AccountCode.CARD_CLEARING, amount, "Clear card receivable")));
    }

}
