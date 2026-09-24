package com.example.erp.ecommerce;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface OnlineOrderRepository extends JpaRepository<OnlineOrder, UUID> {
    List<OnlineOrder> findAllByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    List<OnlineOrder> findAllByOrderByCreatedAtDesc();
    Optional<OnlineOrder> findByIdAndCustomerId(UUID id, UUID customerId);

    @Query("select o.id from OnlineOrder o where o.status = :status and o.expiresAt <= :deadline")
    List<UUID> findDueOrderIds(@Param("status") OnlineOrderStatus status,
                               @Param("deadline") java.time.Instant deadline);
}
