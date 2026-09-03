package com.example.erp.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;

interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, UUID> {
    boolean existsByPaymentReference(String paymentReference);
    Optional<SupplierPayment> findByPaymentReference(String paymentReference);
}
