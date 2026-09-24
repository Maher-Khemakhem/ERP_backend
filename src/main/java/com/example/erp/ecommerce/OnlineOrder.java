package com.example.erp.ecommerce;

import com.example.erp.shared.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Embedded;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "online_order", schema = "ecommerce")
public class OnlineOrder {

    @Id
    private UUID id;

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OnlineOrderStatus status;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "net_amount", nullable = false, precision = 19, scale = 3))
    private Money netAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "vat_amount", nullable = false, precision = 19, scale = 3))
    private Money vatAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_amount", nullable = false, precision = 19, scale = 3))
    private Money totalAmount;

    @Column(name = "payment_reference", unique = true, length = 120)
    private String paymentReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Version
    private long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "online_order_id", nullable = false)
    private List<OnlineOrderLine> lines = new ArrayList<>();

    protected OnlineOrder() {
    }

    private OnlineOrder(UUID customerId, List<OnlineOrderLine> lines,
                        Money netAmount, Money vatAmount, Money totalAmount, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.orderNumber = "ONL-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        this.customerId = Objects.requireNonNull(customerId);
        this.status = OnlineOrderStatus.PENDING_PAYMENT;
        this.netAmount = Objects.requireNonNull(netAmount);
        this.vatAmount = Objects.requireNonNull(vatAmount);
        this.totalAmount = Objects.requireNonNull(totalAmount);
        this.createdAt = Instant.now();
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.lines.addAll(lines);
    }

    static OnlineOrder create(UUID customerId, List<OnlineOrderLine> lines,
                               Money netAmount, Money vatAmount, Money totalAmount) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Cannot create an empty online order");
        }
        return new OnlineOrder(customerId, lines, netAmount, vatAmount, totalAmount,
                Instant.now().plusSeconds(15 * 60L));
    }

    static OnlineOrder create(UUID customerId, List<OnlineOrderLine> lines,
                              Money netAmount, Money vatAmount, Money totalAmount,
                              Instant expiresAt) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Cannot create an empty online order");
        }
        return new OnlineOrder(customerId, lines, netAmount, vatAmount, totalAmount, expiresAt);
    }

    public void confirmPayment(String paymentReference) {
        if (status == OnlineOrderStatus.CONFIRMED) {
            return;
        }
        if (status != OnlineOrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only pending online orders can receive payment confirmation");
        }
        this.paymentReference = Objects.requireNonNull(paymentReference);
        this.confirmedAt = Instant.now();
        this.status = OnlineOrderStatus.CONFIRMED;
    }

    public boolean cancelPendingPayment(Instant now) {
        if (status == OnlineOrderStatus.CANCELLED || status == OnlineOrderStatus.EXPIRED) {
            return false;
        }
        if (status != OnlineOrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only unpaid pending orders can be cancelled");
        }
        this.status = OnlineOrderStatus.CANCELLED;
        this.cancelledAt = Objects.requireNonNull(now);
        return true;
    }

    public boolean expireIfDue(Instant now) {
        if (status != OnlineOrderStatus.PENDING_PAYMENT || now.isBefore(expiresAt)) {
            return false;
        }
        this.status = OnlineOrderStatus.EXPIRED;
        this.expiredAt = now;
        return true;
    }

    public void advanceFulfillment(OnlineOrderStatus nextStatus) {
        Objects.requireNonNull(nextStatus, "nextStatus");
        if (status == nextStatus) {
            return;
        }
        boolean allowed = switch (status) {
            case CONFIRMED -> nextStatus == OnlineOrderStatus.PREPARING;
            case PREPARING -> nextStatus == OnlineOrderStatus.READY;
            case READY -> nextStatus == OnlineOrderStatus.COMPLETED;
            default -> false;
        };
        if (!allowed) {
            throw new IllegalStateException(
                    "Online order cannot move from " + status + " to " + nextStatus);
        }
        this.status = nextStatus;
    }

    public UUID getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public UUID getCustomerId() { return customerId; }
    public OnlineOrderStatus getStatus() { return status; }
    public Money getNetAmount() { return netAmount; }
    public Money getVatAmount() { return vatAmount; }
    public Money getTotalAmount() { return totalAmount; }
    public String getPaymentReference() { return paymentReference; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getExpiredAt() { return expiredAt; }
    public List<OnlineOrderLine> getLines() { return List.copyOf(lines); }
}
