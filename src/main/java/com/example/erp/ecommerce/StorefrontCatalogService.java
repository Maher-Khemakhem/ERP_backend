package com.example.erp.ecommerce;

import com.example.erp.inventory.InventoryCatalog;
import com.example.erp.pricing.PricingEngine;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.RoundingMode;

@Service
public class StorefrontCatalogService {

    private final InventoryCatalog inventoryCatalog;
    private final PricingEngine pricingEngine;

    StorefrontCatalogService(InventoryCatalog inventoryCatalog, PricingEngine pricingEngine) {
        this.inventoryCatalog = inventoryCatalog;
        this.pricingEngine = pricingEngine;
    }

    @Transactional(readOnly = true)
    public List<StorefrontProduct> list(String search, UUID familyId) {
        return inventoryCatalog.findOnlineProducts(search, familyId).stream()
                .map(this::toStorefrontProduct)
                .flatMap(Optional::stream)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<StorefrontProduct> find(UUID productId) {
        return inventoryCatalog.findOnlineProduct(productId)
                .flatMap(this::toStorefrontProduct);
    }

    @Transactional(readOnly = true)
    public Optional<LineQuote> quote(UUID productId, Quantity quantity) {
        return inventoryCatalog.findOnlineProduct(productId)
                .filter(product -> product.unitType() == quantity.unitType())
                .flatMap(product -> pricingEngine.findUnitPrice(productId, quantity.unitType())
                        .map(unitPrice -> {
                            Money lineTotal = pricingEngine.calculateLinePrice(productId, quantity);
                            Money vatAmount = lineTotal.multiply(product.vatRate()
                                    .divide(java.math.BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
                            return new LineQuote(product, quantity, unitPrice, lineTotal, vatAmount);
                        }));
    }

    private Optional<StorefrontProduct> toStorefrontProduct(InventoryCatalog.CatalogProduct product) {
        return pricingEngine.findUnitPrice(product.id(), product.unitType())
                .map(price -> new StorefrontProduct(
                        product.id(),
                        product.sku(),
                        product.name(),
                        product.familyId(),
                        product.familyName(),
                        product.familyImageUrl(),
                        product.imageUrl(),
                        product.unitType().name(),
                        price,
                        product.vatRate(),
                        product.availableQuantity(),
                        product.inStock()));
    }

    public record StorefrontProduct(
            UUID id,
            String sku,
            String name,
            UUID familyId,
            String familyName,
            String familyImageUrl,
            String imageUrl,
            String unitType,
            Money unitPrice,
            BigDecimal vatRate,
            BigDecimal availableQuantity,
            boolean inStock) {
    }

    public record LineQuote(
            InventoryCatalog.CatalogProduct product,
            Quantity quantity,
            Money unitPrice,
            Money lineTotal,
            Money vatAmount) {
    }
}
