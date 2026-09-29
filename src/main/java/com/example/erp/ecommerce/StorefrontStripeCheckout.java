package com.example.erp.ecommerce;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stripe_checkout", schema = "ecommerce")
class StorefrontStripeCheckout {
    @Id
    UUID id;
    @Version
    long version;
    @Column(name = "order_id", nullable = false, unique = true)
    UUID orderId;
    @Column(name = "checkout_session_id", unique = true)
    String checkoutSessionId;
    @Column(name = "checkout_url", columnDefinition = "text")
    String checkoutUrl;
    @Column(name = "tnd_amount", nullable = false, precision = 19, scale = 3)
    BigDecimal tndAmount;
    @Column(name = "eur_cents", nullable = false)
    long eurCents;
    @Column(name = "tnd_to_eur_rate", nullable = false, precision = 18, scale = 8)
    BigDecimal rate;
    @Column(nullable = false)
    String status;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    protected StorefrontStripeCheckout() {}

    StorefrontStripeCheckout(UUID orderId, BigDecimal tndAmount, BigDecimal rate, long eurCents) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.tndAmount = tndAmount;
        this.rate = rate;
        this.eurCents = eurCents;
        this.status = "CREATING";
        this.createdAt = Instant.now();
    }
}
