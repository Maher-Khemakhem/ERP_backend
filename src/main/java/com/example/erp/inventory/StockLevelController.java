package com.example.erp.inventory;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/stock")
class StockLevelController {

    private final InventoryService inventoryService;
    private final ProductRepository productRepository;

    StockLevelController(InventoryService inventoryService, ProductRepository productRepository) {
        this.inventoryService = inventoryService;
        this.productRepository = productRepository;
    }

    public record StockLevelView(UUID productId, String productName, BigDecimal totalQuantity, List<LotView> lots) {}
    public record LotView(String id, String productId, BigDecimal quantity, String expiryDate, String receivedAt) {}

    @GetMapping("/{productId}")
    public ResponseEntity<StockLevelView> get(@PathVariable UUID productId) {
        StockLevel stock = inventoryService.getStockLevel(productId);

        String productName = productRepository.findById(productId)
                .map(Product::getName)
                .orElse("");

        BigDecimal totalQuantity = stock.getLots().stream()
                .map(e -> e.onHand().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LotView> lots = stock.getLots().stream()
                .filter(e -> !e.onHand().isZero())
                .map(e -> new LotView(
                        e.lot().getId().toString(),
                        e.lot().getProductId().toString(),
                        e.onHand().value(),
                        e.lot().getExpiryDate() != null ? e.lot().getExpiryDate().toString() : null,
                        e.lot().getReceivedDate() != null ? e.lot().getReceivedDate().atStartOfDay().toString() : null
                ))
                .toList();

        return ResponseEntity.ok(new StockLevelView(productId, productName, totalQuantity, lots));
    }
}
