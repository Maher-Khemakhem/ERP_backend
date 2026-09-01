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
public class InventoryService {

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
        validateProductForSale(productId, quantity.unitType());

        StockLevel stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Aucun stock trouvé pour le produit : " + productId));
        int reorderPoint = reorderPointFor(productId);

        stock.sell(quantity, LocalDate.now(), reorderPoint);

        stockRepository.save(stock);
        publishAndClear(stock);
    }

    /** Validates the catalog contract used by POS before pricing or selling. */
    @Transactional(readOnly = true)
    public void validateProductForSale(UUID productId, Quantity.UnitType unitType) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Product not found: " + productId));

        if (product.getUnitType() != unitType) {
            throw new IllegalArgumentException(
                    "Requested unit type does not match the product: " + productId);
        }
    }

    @Transactional
    public boolean trySell(UUID productId, Quantity quantity) {
        try {
            sell(productId, quantity);
            return true;
        } catch (StockLevel.InsufficientStockException ex) {
            return false;
        }
    }

    @Transactional
    public void receive(UUID productId, Lot lot, Quantity quantity) {
        validateProductForSale(productId, quantity.unitType());
        if (!productId.equals(lot.getProductId())) {
            throw new IllegalArgumentException("Lot product does not match the stock product");
        }
        if (lot.getExpiryDate() == null && productRepository.findById(productId).orElseThrow().isPerishable()) {
            throw new IllegalArgumentException("Perishable products require an expiry date");
        }

        StockLevel stock = stockRepository.findByProductId(productId)
                .orElseGet(() -> StockLevel.initialize(productId));

        stock.receive(lot, quantity);

        stockRepository.save(stock);
        publishAndClear(stock);
    }

    /** Receives stock from another module without exposing Inventory's Lot entity. */
    @Transactional
    public void receiveStock(UUID productId, Quantity quantity, String lotNumber,
                             LocalDate expiryDate, LocalDate receivedDate,
                             String supplierReference) {
        String effectiveLotNumber = lotNumber == null || lotNumber.isBlank()
                ? "LOT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                : lotNumber.trim();
        LocalDate effectiveReceivedDate = receivedDate == null ? LocalDate.now() : receivedDate;
        Lot lot = Lot.receive(effectiveLotNumber, productId, expiryDate,
                effectiveReceivedDate, supplierReference);
        receive(productId, lot, quantity);
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
    @Transactional(readOnly = true)
    public StockLevel getStockLevel(UUID productId) {
        return stockRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("Aucun stock pour ce produit : " + productId));
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
