package com.example.erp.pos;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

interface CashSessionRepository extends JpaRepository<CashSession, UUID> {

    /** Utile pour retrouver la session active d'une caisse au moment du scan. */
    List<CashSession> findByTillIdAndStatusOrderByOpenedAtDesc(UUID tillId, CashSession.SessionStatus status);
}
