package com.example.erp.finance;

import com.example.erp.pos.CashSessionClosed;
import com.example.erp.shared.Money;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Enregistre l'écart de caisse (s'il y en a un) à chaque clôture de
 * session. On enregistre toujours une écriture de réconciliation,
 * même à écart zéro — c'est la trace d'audit qui compte, pas
 * uniquement les anomalies.
 */
@Component
class CashSessionClosedListener {

    private final LedgerEntryRepository ledgerRepository;

    CashSessionClosedListener(LedgerEntryRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @ApplicationModuleListener
    void on(CashSessionClosed event) {
        Money variance = event.variance();
        String description = variance.equals(Money.zero())
                ? "Réconciliation de caisse - session " + event.cashSessionId() + " (aucun écart)"
                : "Réconciliation de caisse - session " + event.cashSessionId() + " (écart détecté)";

        LedgerEntry entry = LedgerEntry.cashVariance(variance, description, event.cashSessionId());
        ledgerRepository.save(entry);
    }
}