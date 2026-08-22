package com.example.erp.finance;

import com.example.erp.pos.TransactionCompleted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Enregistre le chiffre d'affaires à chaque vente finalisée.
 * Même principe que le listener d'Inventory sur ce même événement :
 * Finance et Inventory réagissent tous les deux, indépendamment, au
 * même fait publié par POS.
 */
@Component
class FinanceTransactionCompletedListener {

    private final LedgerEntryRepository ledgerRepository;

    FinanceTransactionCompletedListener(LedgerEntryRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @ApplicationModuleListener
    void on(TransactionCompleted event) {
        LedgerEntry entry = LedgerEntry.revenue(
                event.total(),
                "Vente - ticket " + event.transactionId(),
                event.transactionId()
        );
        ledgerRepository.save(entry);
    }
}