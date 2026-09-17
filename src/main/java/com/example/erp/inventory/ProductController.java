package com.example.erp.inventory;

import com.example.erp.shared.Quantity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/products")
class ProductController {
    private final ProductRepository productRepository;
    private final ProductFamilyRepository familyRepository;

    ProductController(ProductRepository productRepository, ProductFamilyRepository familyRepository) {
        this.productRepository = productRepository;
        this.familyRepository = familyRepository;
    }

    public record CreateProductRequest(
            @NotBlank(message = "SKU is required") String sku,
            @NotBlank(message = "name is required") String name,
            UUID familyId,
            @Size(max = 500, message = "image URL cannot exceed 500 characters") String imageUrl,
            @NotNull(message = "unitType is required") String unitType,
            boolean perishable,
            @Min(value = 0, message = "reorder point cannot be negative") int reorderPoint,
            @DecimalMin(value = "0.0", message = "VAT rate cannot be negative")
            @DecimalMax(value = "100.0", message = "VAT rate cannot exceed 100") BigDecimal vatRate) {
    }

            public record ProductView(UUID id, String sku, String name, UUID familyId, String familyName, String familyImageUrl, String imageUrl, String unitType,
                              boolean perishable, int reorderPoint, BigDecimal vatRate) {
        static ProductView from(Product p) {
                ProductFamily family = p.getFamily();
                return new ProductView(p.getId(), p.getSku(), p.getName(), family == null ? null : family.getId(),
                    family == null ? null : family.getName(), family == null ? null : family.getImageUrl(), p.getImageUrl(), p.getUnitType().name(),
                    p.isPerishable(), p.getReorderPoint(), p.getVatRate());
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> create(@Valid @RequestBody CreateProductRequest request) {
        ProductFamily family = request.familyId() == null ? null : familyRepository.findById(request.familyId())
            .orElseThrow(() -> new IllegalArgumentException("Product family not found: " + request.familyId()));
        Product product = Product.create(request.sku(), request.name(), family, request.imageUrl(),
            Quantity.UnitType.valueOf(request.unitType()),
                request.perishable(), request.reorderPoint(),
                request.vatRate() == null ? BigDecimal.valueOf(19) : request.vatRate());
        productRepository.save(product);
        return ResponseEntity.ok(Map.of("productId", product.getId()));
    }

    @GetMapping
    public ResponseEntity<List<ProductView>> list() {
        return ResponseEntity.ok(productRepository.findAll().stream().map(ProductView::from).toList());
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductView> get(@PathVariable UUID productId) {
        return productRepository.findById(productId).map(ProductView::from)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> delete(@PathVariable UUID productId) {
        if (!productRepository.existsById(productId)) {
            return ResponseEntity.notFound().build();
        }
        try {
            productRepository.deleteById(productId);
            productRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(409).build();
        }
        return ResponseEntity.noContent().build();
    }
}
