package com.example.erp.pos;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PosTransactionRepository extends JpaRepository<PosTransaction, UUID> {
    List<PosTransaction> findAllByOrderByStartedAtDesc();
}
