package com.example.erp.procurement;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
}