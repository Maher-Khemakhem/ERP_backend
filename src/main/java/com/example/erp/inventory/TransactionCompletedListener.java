package com.example.erp.inventory;

import com.example.erp.pos.TransactionCompleted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Écoute TransactionCompleted (publié par le module POS) et déduit le
 * stock correspondant pour chaque ligne vendue.
 *
 * C'est ICI, concrètement, que POS et Inventory se parlent : pas un
 * appel de méthode direct entre les deux modules, mais une réaction à
 * un événement. Si POS est un jour extrait en microservice séparé,
 * cette classe ne change quasiment pas — seul le transport de
 * l'événement change (bus interne → RabbitMQ/Kafka).
 *
 * @ApplicationModuleListener (et non @EventListener classique) est
 * l'annotation Spring Modulith : elle garantit l'exécution APRÈS le
 * commit de la transaction qui a publié l'événement, et bénéficie du
 * registre de publication (outbox) — si cette méthode échoue ou si
 * l'app crashe pendant son exécution, Modulith réessaiera au redémarrage.
 */
@Component
class TransactionCompletedListener {

    private final InventoryService inventoryService;

    TransactionCompletedListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @ApplicationModuleListener
    void on(TransactionCompleted event) {
        for (TransactionCompleted.LineItem line : event.lines()) {
            inventoryService.sell(line.productId(), line.quantity());
        }
    }
}