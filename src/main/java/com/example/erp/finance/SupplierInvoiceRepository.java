package com.example.erp.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, UUID> {
    boolean existsByInvoiceNumber(String invoiceNumber);
    List<SupplierInvoice> findAllByOrderByCreatedAtDesc();
}
