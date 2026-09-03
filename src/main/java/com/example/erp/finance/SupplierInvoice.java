package com.example.erp.finance;

import com.example.erp.shared.Money;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "supplier_invoice", schema = "finance")
public class SupplierInvoice {
    @Id
    private UUID id;
    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;
    @Column(name = "purchase_order_id")
    private UUID purchaseOrderId;
    @Column(name = "invoice_number", nullable = false, unique = true)
    private String invoiceNumber;
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "subtotal", nullable = false, precision = 19, scale = 3))
    private Money subtotal;
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "tax_amount", nullable = false, precision = 19, scale = 3))
    private Money taxAmount;
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_amount", nullable = false, precision = 19, scale = 3))
    private Money total;
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "paid_amount", nullable = false, precision = 19, scale = 3))
    private Money paidAmount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;
    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Version
    private long version;

    protected SupplierInvoice() {
    }

    private SupplierInvoice(UUID supplierId, UUID purchaseOrderId, String invoiceNumber,
                            Money subtotal, Money taxAmount, Money total,
                            LocalDate issueDate, LocalDate dueDate) {
        this.id = UUID.randomUUID();
        this.supplierId = Objects.requireNonNull(supplierId);
        this.purchaseOrderId = purchaseOrderId;
        this.invoiceNumber = requireText(invoiceNumber);
        this.subtotal = subtotal;
        this.taxAmount = taxAmount;
        this.total = total;
        this.paidAmount = Money.zero();
        this.status = InvoiceStatus.OPEN;
        this.issueDate = Objects.requireNonNull(issueDate);
        this.dueDate = dueDate;
        this.createdAt = Instant.now();
    }

    static SupplierInvoice create(UUID supplierId, UUID purchaseOrderId, String invoiceNumber,
                                  Money subtotal, Money taxAmount, Money total,
                                  LocalDate issueDate, LocalDate dueDate) {
        if (subtotal.isNegative() || taxAmount.isNegative() || total.isNegative()) {
            throw new IllegalArgumentException("Invoice amounts cannot be negative");
        }
        if (dueDate != null && dueDate.isBefore(issueDate)) {
            throw new IllegalArgumentException("Due date cannot be before issue date");
        }
        return new SupplierInvoice(supplierId, purchaseOrderId, invoiceNumber, subtotal, taxAmount, total,
                issueDate, dueDate);
    }

    public void recordPayment(Money amount) {
        if (status == InvoiceStatus.CANCELLED) throw new IllegalStateException("Cancelled invoices cannot be paid");
        if (amount.isNegative() || amount.isGreaterThan(total.subtract(paidAmount))) {
            throw new IllegalArgumentException("Payment exceeds the outstanding invoice balance");
        }
        paidAmount = paidAmount.add(amount);
        status = paidAmount.amount().compareTo(total.amount()) == 0 ? InvoiceStatus.PAID : InvoiceStatus.PARTIALLY_PAID;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("invoiceNumber is required");
        return value.trim();
    }

    public UUID getId() { return id; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getPurchaseOrderId() { return purchaseOrderId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public Money getSubtotal() { return subtotal; }
    public Money getTaxAmount() { return taxAmount; }
    public Money getTotal() { return total; }
    public Money getPaidAmount() { return paidAmount; }
    public InvoiceStatus getStatus() { return status; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public Instant getCreatedAt() { return createdAt; }

    public enum InvoiceStatus { OPEN, PARTIALLY_PAID, PAID, CANCELLED }
}
