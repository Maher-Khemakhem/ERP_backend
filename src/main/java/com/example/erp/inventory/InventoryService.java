package com.example.erp.inventory;

import com.example.erp.shared.DomainEvent;
import com.example.erp.shared.Quantity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service applicatif du module Inventory — orchestration des cas
 * d'usage (use cases). SEULE porte d'entrée publique du module pour
 * agir sur le stock ; les autres modules (ou contrôleurs REST) passent
 * par ces méthodes, jamais par StockLevel directement.
 *
 * Pattern appliqué systématiquement : charger l'aggregate → appeler
 * son comportement → sauvegarder → publier les événements accumulés,
 * le tout dans une seule transaction (garantie outbox via Spring
 * Modulith : la sauvegarde et l'enregistrement des événements sont
 * atomiques).
 */
@Service
class InventoryService {

    private final StockLevelRepository stockRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher events;

    InventoryService(StockLevelRepository stockRepository,
                     ProductRepository productRepository,
                     ApplicationEventPublisher events) {
        this.stockRepository = stockRepository;
        this.productRepository = productRepository;
        this.events = events;
    }

    @Transactional
    public void sell(UUID productId, Quantity quantity) {
        StockLevel stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Aucun stock trouvé pour le produit : " + productId));
        int reorderPoint = reorderPointFor(productId);

        stock.sell(quantity, LocalDate.now(), reorderPoint);

        stockRepository.save(stock);
        publishAndClear(stock);
    }

    @Transactional
    public void receive(UUID productId, Lot lot, Quantity quantity) {
        StockLevel stock = stockRepository.findByProductId(productId)
                .orElseGet(() -> StockLevel.initialize(productId));

        stock.receive(lot, quantity);

        stockRepository.save(stock);
        publishAndClear(stock);
    }

    @Transactional
    public void writeOff(UUID productId, UUID lotId, Quantity quantity, String reason) {
        StockLevel stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Aucun stock trouvé pour le produit : " + productId));
        int reorderPoint = reorderPointFor(productId);

        stock.writeOff(lotId, quantity, reason, reorderPoint);

        stockRepository.save(stock);
        publishAndClear(stock);
    }

    private int reorderPointFor(UUID productId) {
        return productRepository.findById(productId)
                .map(Product::getReorderPoint)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Produit introuvable : " + productId));
    }

    /**
     * Publie tous les événements accumulés par l'aggregate pendant
     * l'opération, puis les vide. Spring Modulith intercepte chaque
     * publish() et l'enregistre dans son registre (outbox) avant de
     * router l'événement vers les @ApplicationModuleListener concernés.
     */
    private void publishAndClear(StockLevel stock) {
        List<DomainEvent> pending = stock.domainEvents();
        pending.forEach(events::publishEvent);
        stock.clearDomainEvents();
    }
}