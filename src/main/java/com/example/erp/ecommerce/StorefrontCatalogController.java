package com.example.erp.ecommerce;

import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/store/products")
public class StorefrontCatalogController {

    private final StorefrontCatalogService catalogService;

    StorefrontCatalogController(StorefrontCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public List<ProductView> list(
            @RequestParam(required = false) @Size(max = 120) String search,
            @RequestParam(required = false) UUID familyId) {
        return catalogService.list(search, familyId).stream().map(ProductView::from).toList();
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductView> get(@PathVariable UUID productId) {
        return catalogService.find(productId)
                .map(ProductView::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record ProductView(
            UUID id,
            String sku,
            String name,
            UUID familyId,
            String familyName,
            String familyImageUrl,
            String imageUrl,
            String unitType,
            String unitPrice,
            BigDecimal vatRate,
            BigDecimal availableQuantity,
            boolean inStock) {

        static ProductView from(StorefrontCatalogService.StorefrontProduct product) {
            return new ProductView(
                    product.id(),
                    product.sku(),
                    product.name(),
                    product.familyId(),
                    product.familyName(),
                    product.familyImageUrl(),
                    product.imageUrl(),
                    product.unitType(),
                    product.unitPrice().toString(),
                    product.vatRate(),
                    product.availableQuantity(),
                    product.inStock());
        }
    }
}
