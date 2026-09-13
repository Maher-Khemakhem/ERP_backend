package com.example.erp.inventory;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inventory/stock")
class StockLevelController {

    private final ProductRepository productRepository;
    private final StockLevelRepository stockLevelRepository;

    StockLevelController(ProductRepository productRepository, StockLevelRepository stockLevelRepository) {
        this.productRepository = productRepository;
        this.stockLevelRepository = stockLevelRepository;
    }

    public record StockSummaryView(
            UUID productId,
            String sku,
            String name,
            UUID familyId,
            String familyName,
            String familyImageUrl,
            String imageUrl,
            String unitType,
            boolean perishable,
            int reorderPoint,
            BigDecimal totalQuantity,
            int lotCount,
            String earliestExpiryDate,
            String status
    ) {}

    public record StockLevelView(UUID productId, String productName, BigDecimal totalQuantity, List<LotView> lots) {}

    public record LotView(
            String id,
            String lotNumber,
            String productId,
            BigDecimal quantity,
            String expiryDate,
            String receivedAt,
            BigDecimal unitCost,
            String supplierReference
    ) {}

    @GetMapping
    public ResponseEntity<List<StockSummaryView>> listAll() {
        List<Product> products = productRepository.findAll();
        Map<UUID, StockLevel> stockMap = stockLevelRepository.findAll().stream()
                .collect(Collectors.toMap(StockLevel::getProductId, s -> s, (a, b) -> a));

        List<StockSummaryView> summaries = products.stream().map(p -> {
            StockLevel stock = stockMap.get(p.getId());
            BigDecimal totalQuantity = BigDecimal.ZERO;
            int lotCount = 0;
            LocalDate earliestExpiry = null;

            if (stock != null) {
                for (LotEntry entry : stock.getLots()) {
                    if (!entry.onHand().isZero()) {
                        totalQuantity = totalQuantity.add(entry.onHand().value());
                        lotCount++;
                        LocalDate exp = entry.lot().getExpiryDate();
                        if (exp != null) {
                            if (earliestExpiry == null || exp.isBefore(earliestExpiry)) {
                                earliestExpiry = exp;
                            }
                        }
                    }
                }
            }

            String status;
            if (totalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                status = "OUT_OF_STOCK";
            } else if (totalQuantity.compareTo(BigDecimal.valueOf(p.getReorderPoint())) <= 0) {
                status = "LOW_STOCK";
            } else {
                status = "IN_STOCK";
            }

            ProductFamily family = p.getFamily();
            return new StockSummaryView(
                    p.getId(),
                    p.getSku(),
                    p.getName(),
                    family != null ? family.getId() : null,
                    family != null ? family.getName() : null,
                    family != null ? family.getImageUrl() : null,
                    p.getImageUrl(),
                    p.getUnitType().name(),
                    p.isPerishable(),
                    p.getReorderPoint(),
                    totalQuantity,
                    lotCount,
                    earliestExpiry != null ? earliestExpiry.toString() : null,
                    status
            );
        }).toList();

        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<StockLevelView> get(@PathVariable UUID productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }

        String productName = product.getName();
        Optional<StockLevel> stockOpt = stockLevelRepository.findByProductId(productId);

        if (stockOpt.isEmpty()) {
            return ResponseEntity.ok(new StockLevelView(productId, productName, BigDecimal.ZERO, List.of()));
        }

        StockLevel stock = stockOpt.get();
        BigDecimal totalQuantity = stock.getLots().stream()
                .map(e -> e.onHand().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LotView> lots = stock.getLots().stream()
                .filter(e -> !e.onHand().isZero())
                .map(e -> new LotView(
                        e.lot().getId().toString(),
                        e.lot().getLotNumber(),
                        e.lot().getProductId().toString(),
                        e.onHand().value(),
                        e.lot().getExpiryDate() != null ? e.lot().getExpiryDate().toString() : null,
                        e.lot().getReceivedDate() != null ? e.lot().getReceivedDate().atStartOfDay().toString() : null,
                        e.lot().getUnitCost() != null ? e.lot().getUnitCost().amount() : BigDecimal.ZERO,
                        e.lot().getSupplierReference()
                ))
                .toList();

        return ResponseEntity.ok(new StockLevelView(productId, productName, totalQuantity, lots));
    }
}
