package com.example.erp.pos;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

interface CashSessionRepository extends JpaRepository<CashSession, UUID> {

    /** Utile pour retrouver la session active d'une caisse au moment du scan. */
    Optional<CashSession> findByTillIdAndStatus(UUID tillId, CashSession.SessionStatus status);
}