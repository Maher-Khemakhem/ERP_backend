package com.example.erp.ecommerce;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface OnlineOrderRepository extends JpaRepository<OnlineOrder, UUID> {
    List<OnlineOrder> findAllByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    Optional<OnlineOrder> findByIdAndCustomerId(UUID id, UUID customerId);
}
