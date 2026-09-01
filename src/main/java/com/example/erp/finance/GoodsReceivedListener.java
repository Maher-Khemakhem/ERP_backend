package com.example.erp.finance;

import com.example.erp.procurement.GoodsReceived;
import com.example.erp.shared.Money;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Enregistre une dette fournisseur (AP) à chaque réception de marchandise. */
@Component
class GoodsReceivedListener {

    private final LedgerEntryRepository ledgerRepository;

    GoodsReceivedListener(LedgerEntryRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @ApplicationModuleListener
    void on(GoodsReceived event) {
        Money totalCost = event.lines().stream()
                .map(line -> line.unitCost().multiply(line.quantity().value()))
                .reduce(Money.zero(), Money::add);

        LedgerEntry entry = LedgerEntry.accountsPayable(
                totalCost,
                "Réception marchandise - commande " + event.purchaseOrderId(),
                event.purchaseOrderId()
        );
        ledgerRepository.save(entry);
    }
}
