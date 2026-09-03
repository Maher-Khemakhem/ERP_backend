package com.example.erp.finance;

import com.example.erp.shared.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMax;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance")
class FinanceController {
    private final JournalEntryRepository journalRepository;
    private final LedgerEntryRepository legacyLedgerRepository;
    private final SupplierInvoiceRepository invoiceRepository;
    private final FinanceService financeService;

    FinanceController(JournalEntryRepository journalRepository, LedgerEntryRepository legacyLedgerRepository,
                      SupplierInvoiceRepository invoiceRepository, FinanceService financeService) {
        this.journalRepository = journalRepository;
        this.legacyLedgerRepository = legacyLedgerRepository;
        this.invoiceRepository = invoiceRepository;
        this.financeService = financeService;
    }

    public record LedgerEntryView(UUID id, String entryType, String amount, String description,
                                  UUID referenceId, String createdAt) {
    }

    public record JournalLineView(UUID id, String account, String debit, String credit, String description) {
        static JournalLineView from(JournalLine line) {
            return new JournalLineView(line.getId(), line.getAccountCode().name(), line.getDebit().toString(),
                    line.getCredit().toString(), line.getDescription());
        }
    }

    public record JournalView(UUID id, String eventKey, String description, UUID referenceId,
                              String postedAt, List<JournalLineView> lines) {
        static JournalView from(JournalEntry entry) {
            return new JournalView(entry.getId(), entry.getEventKey(), entry.getDescription(), entry.getReferenceId(),
                    entry.getPostedAt().toString(), entry.getLines().stream().map(JournalLineView::from).toList());
        }
    }

    /** Backward-compatible flattened view for the existing finance screen. */
    @GetMapping("/ledger")
    public ResponseEntity<List<LedgerEntryView>> listLedger() {
        List<LedgerEntryView> result = new ArrayList<>();
        journalRepository.findAllByOrderByPostedAtDesc().forEach(entry -> entry.getLines().forEach(line -> {
            Money amount = line.getDebit().isZero() ? line.getCredit() : line.getDebit();
            result.add(new LedgerEntryView(line.getId(), line.getAccountCode().name(), amount.toString(),
                    entry.getDescription() + " - " + line.getDescription(), entry.getReferenceId(), entry.getPostedAt().toString()));
        }));
        legacyLedgerRepository.findAllByOrderByRecordedAtDesc().forEach(entry -> result.add(new LedgerEntryView(
                entry.getId(), entry.getType().name(), entry.getAmount().toString(), entry.getDescription(),
                entry.getReferenceId(), entry.getRecordedAt().toString())));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/journals")
    public ResponseEntity<List<JournalView>> journals() {
        return ResponseEntity.ok(journalRepository.findAllByOrderByPostedAtDesc().stream().map(JournalView::from).toList());
    }

    public record ProfitAndLossView(String from, String to, String revenue, String outputVat,
                                   String costOfGoodsSold, String cashVariance, String grossProfit, String netProfit) {
    }

    @GetMapping("/reports/profit-and-loss")
    public ResponseEntity<ProfitAndLossView> profitAndLoss(
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        if (end.isBefore(start)) throw new IllegalArgumentException("to cannot be before from");
        Instant fromInstant = start.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toInstant = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Money revenue = Money.zero();
        Money vat = Money.zero();
        Money cogs = Money.zero();
        Money cashVariance = Money.zero();
        for (JournalEntry entry : journalRepository.findByPostedAtBetweenOrderByPostedAtAsc(fromInstant, toInstant)) {
            for (JournalLine line : entry.getLines()) {
                if (line.getAccountCode() == AccountCode.SALES_REVENUE) revenue = revenue.add(line.getCredit()).subtract(line.getDebit());
                if (line.getAccountCode() == AccountCode.OUTPUT_VAT) vat = vat.add(line.getCredit()).subtract(line.getDebit());
                if (line.getAccountCode() == AccountCode.COST_OF_GOODS_SOLD) cogs = cogs.add(line.getDebit()).subtract(line.getCredit());
                if (line.getAccountCode() == AccountCode.CASH_VARIANCE) cashVariance = cashVariance.add(line.getCredit()).subtract(line.getDebit());
            }
        }
        Money grossProfit = revenue.subtract(cogs);
        Money netProfit = grossProfit.add(cashVariance);
        return ResponseEntity.ok(new ProfitAndLossView(start.toString(), end.toString(), revenue.toString(), vat.toString(),
                cogs.toString(), cashVariance.toString(), grossProfit.toString(), netProfit.toString()));
    }

    public record CreateInvoiceRequest(
            @NotNull UUID supplierId, UUID purchaseOrderId, @NotBlank String invoiceNumber,
            @NotNull @Positive BigDecimal subtotal, @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal vatRate,
            LocalDate issueDate, LocalDate dueDate) {
    }

    public record InvoiceView(UUID id, UUID supplierId, UUID purchaseOrderId, String invoiceNumber,
                              String subtotal, String taxAmount, String total, String paidAmount,
                              String outstanding, String status, String issueDate, String dueDate) {
        static InvoiceView from(SupplierInvoice i) {
            return new InvoiceView(i.getId(), i.getSupplierId(), i.getPurchaseOrderId(), i.getInvoiceNumber(),
                    i.getSubtotal().toString(), i.getTaxAmount().toString(), i.getTotal().toString(),
                    i.getPaidAmount().toString(), i.getTotal().subtract(i.getPaidAmount()).toString(),
                    i.getStatus().name(), i.getIssueDate().toString(), i.getDueDate() == null ? null : i.getDueDate().toString());
        }
    }

    @GetMapping("/supplier-invoices")
    public ResponseEntity<List<InvoiceView>> invoices() {
        return ResponseEntity.ok(invoiceRepository.findAllByOrderByCreatedAtDesc().stream().map(InvoiceView::from).toList());
    }

    @PostMapping("/supplier-invoices")
    public ResponseEntity<InvoiceView> createInvoice(@Valid @RequestBody CreateInvoiceRequest request) {
        SupplierInvoice invoice = financeService.createInvoice(request.supplierId(), request.purchaseOrderId(),
                request.invoiceNumber(), Money.of(request.subtotal()), request.vatRate(),
                request.issueDate() == null ? LocalDate.now() : request.issueDate(), request.dueDate());
        return ResponseEntity.ok(InvoiceView.from(invoice));
    }

    public record PayInvoiceRequest(@NotBlank String paymentReference, @NotNull @Positive BigDecimal amount,
                                    @NotBlank String paymentMethod, LocalDate paidDate) {
    }

    @PostMapping("/supplier-invoices/{invoiceId}/payments")
    public ResponseEntity<InvoiceView> payInvoice(@PathVariable UUID invoiceId,
                                                  @Valid @RequestBody PayInvoiceRequest request) {
        SupplierInvoice invoice = financeService.payInvoice(invoiceId, request.paymentReference(), Money.of(request.amount()),
                request.paymentMethod(), request.paidDate() == null ? LocalDate.now() : request.paidDate());
        return ResponseEntity.ok(InvoiceView.from(invoice));
    }

    public record CardSettlementRequest(@NotBlank String settlementReference, @NotNull @Positive BigDecimal amount,
                                        LocalDate settlementDate) {
    }

    @PostMapping("/settlements/card")
    public ResponseEntity<Void> settleCard(@Valid @RequestBody CardSettlementRequest request) {
        financeService.settleCard(request.settlementReference(), Money.of(request.amount()),
                request.settlementDate() == null ? LocalDate.now() : request.settlementDate());
        return ResponseEntity.ok().build();
    }
}
