package com.example.erp.procurement;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

interface LowStockAlertRepository extends JpaRepository<LowStockAlert, UUID> {
    List<LowStockAlert> findByAcknowledgedFalse();
}