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
 * Service applicatif du module Inventory — SEULE porte d'entrée
 * publique pour agir sur le stock.
 *
 * Pattern appliqué systématiquement : charger l'aggregate → appeler
 * son comportement → sauvegarder → publier les événements accumulés,
 * dans une seule transaction.
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

    private void publishAndClear(StockLevel stock) {
        List<DomainEvent> pending = stock.domainEvents();
        pending.forEach(events::publishEvent);
        stock.clearDomainEvents();
    }
}