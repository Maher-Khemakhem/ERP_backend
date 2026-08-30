package com.example.erp.inventory;

import com.example.erp.shared.Quantity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.*;


import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/products")
class ProductController {

    private final ProductRepository productRepository;

    ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }


    public record CreateProductRequest(
            @NotBlank(message = "le SKU est requis") String sku,
            @NotBlank(message = "le nom est requis") String name,
            @NotNull(message = "le type d'unité est requis") String unitType,
            boolean perishable,
            @Min(value = 0, message = "le seuil de réapprovisionnement ne peut pas être négatif") int reorderPoint) {
    }

    public record ProductView(
            UUID id, String sku, String name, String unitType, boolean perishable, int reorderPoint) {

        static ProductView from(Product p) {
            return new ProductView(p.getId(), p.getSku(), p.getName(),
                    p.getUnitType().name(), p.isPerishable(), p.getReorderPoint());
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> create(@Valid @RequestBody CreateProductRequest request) {
        Product product = Product.create(
                request.sku(),
                request.name(),
                Quantity.UnitType.valueOf(request.unitType()),
                request.perishable(),
                request.reorderPoint()
        );
        productRepository.save(product);
        return ResponseEntity.ok(Map.of("productId", product.getId()));
    }

    @GetMapping
    public ResponseEntity<List<ProductView>> list() {
        List<ProductView> products = productRepository.findAll().stream()
                .map(ProductView::from)
                .toList();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductView> get(@PathVariable UUID productId) {
        return productRepository.findById(productId)
                .map(ProductView::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}