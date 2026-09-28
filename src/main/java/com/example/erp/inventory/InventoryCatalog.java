package com.example.erp.inventory;

import com.example.erp.shared.Quantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only catalog boundary for other modules.
 *
 * The storefront receives immutable DTOs rather than Product or StockLevel
 * entities, and therefore cannot accidentally reach inventory persistence or
 * mutate stock outside InventoryService.
 */
@Service
public class InventoryCatalog {

    private final ProductRepository productRepository;
    private final StockLevelRepository stockLevelRepository;

    InventoryCatalog(ProductRepository productRepository, StockLevelRepository stockLevelRepository) {
        this.productRepository = productRepository;
        this.stockLevelRepository = stockLevelRepository;
    }

    @Transactional(readOnly = true)
    public List<CatalogProduct> findOnlineProducts(String search, UUID familyId) {
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase();
        return productRepository.findAll().stream()
                .filter(Product::isOnlineVisible)
                .filter(product -> familyId == null
                        || product.getFamily() != null && familyId.equals(product.getFamily().getId()))
                .filter(product -> normalizedSearch.isBlank()
                        || product.getName().toLowerCase().contains(normalizedSearch)
                        || product.getSku().toLowerCase().contains(normalizedSearch))
                .map(this::toCatalogProduct)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<CatalogProduct> findOnlineProduct(UUID productId) {
        return productRepository.findById(productId)
                .filter(Product::isOnlineVisible)
                .map(this::toCatalogProduct);
    }

    private CatalogProduct toCatalogProduct(Product product) {
        ProductFamily family = product.getFamily();
        BigDecimal available = stockLevelRepository.findByProductId(product.getId())
                .map(stock -> stock.getLots().stream()
                        .map(entry -> entry.available().value())
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .orElse(BigDecimal.ZERO);
        return new CatalogProduct(
                product.getId(),
                product.getSku(),
                product.getName(),
                family == null ? null : family.getId(),
                family == null ? null : family.getName(),
                family == null ? null : family.getImageUrl(),
                product.getImageUrl(),
                product.getUnitType(),
                product.getVatRate(),
                available);
    }

    public record CatalogProduct(
            UUID id,
            String sku,
            String name,
            UUID familyId,
            String familyName,
            String familyImageUrl,
            String imageUrl,
            Quantity.UnitType unitType,
            BigDecimal vatRate,
            BigDecimal availableQuantity) {

        public boolean inStock() {
            return availableQuantity.signum() > 0;
        }
    }
}
