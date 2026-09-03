package com.example.erp.finance;

import com.example.erp.shared.Money;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "supplier_payment", schema = "finance")
public class SupplierPayment {
    @Id
    private UUID id;
    @Column(name = "payment_reference", nullable = false, unique = true)
    private String paymentReference;
    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false, precision = 19, scale = 3))
    private Money amount;
    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;
    @Column(name = "paid_date", nullable = false)
    private LocalDate paidDate;

    protected SupplierPayment() {
    }

    private SupplierPayment(String paymentReference, UUID invoiceId, Money amount,
                            String paymentMethod, LocalDate paidDate) {
        this.id = UUID.randomUUID();
        this.paymentReference = paymentReference;
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.paidDate = paidDate;
    }

    static SupplierPayment create(String paymentReference, UUID invoiceId, Money amount,
                                  String paymentMethod, LocalDate paidDate) {
        if (paymentReference == null || paymentReference.isBlank()) throw new IllegalArgumentException("paymentReference is required");
        if (amount.isNegative() || amount.isZero()) throw new IllegalArgumentException("Payment must be positive");
        String normalizedMethod = paymentMethod == null || paymentMethod.isBlank() ? "BANK" : paymentMethod.trim().toUpperCase();
        if (!normalizedMethod.equals("BANK") && !normalizedMethod.equals("CASH")) {
            throw new IllegalArgumentException("Payment method must be BANK or CASH");
        }
        return new SupplierPayment(paymentReference.trim(), invoiceId, amount,
                normalizedMethod,
                paidDate);
    }

    public UUID getId() { return id; }
    public String getPaymentReference() { return paymentReference; }
    public UUID getInvoiceId() { return invoiceId; }
    public Money getAmount() { return amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public LocalDate getPaidDate() { return paidDate; }
}
