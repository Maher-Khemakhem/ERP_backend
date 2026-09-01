package com.example.erp.pos;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CashSessionRepository extends JpaRepository<CashSession, UUID> {
    List<CashSession> findByTillIdAndStatusOrderByOpenedAtDesc(UUID tillId, CashSession.SessionStatus status);
    List<CashSession> findByTillIdOrderByOpenedAtDesc(UUID tillId);
}
