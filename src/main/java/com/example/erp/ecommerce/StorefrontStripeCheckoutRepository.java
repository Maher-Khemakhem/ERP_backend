package com.example.erp.ecommerce;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.List;

interface StorefrontStripeCheckoutRepository extends JpaRepository<StorefrontStripeCheckout, UUID> {
    Optional<StorefrontStripeCheckout> findByOrderId(UUID orderId);
    boolean existsByOrderId(UUID orderId);
    List<StorefrontStripeCheckout> findByOrderIdIn(Collection<UUID> orderIds);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from StorefrontStripeCheckout c where c.orderId = :orderId")
    Optional<StorefrontStripeCheckout> lockByOrderId(UUID orderId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from StorefrontStripeCheckout c where c.checkoutSessionId = :sessionId")
    Optional<StorefrontStripeCheckout> lockBySessionId(String sessionId);
}
