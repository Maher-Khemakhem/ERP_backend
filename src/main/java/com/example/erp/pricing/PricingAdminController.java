package com.example.erp.pricing;

import com.example.erp.shared.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/pricing")
class PricingAdminController {

    private final ProductPriceRepository priceRepository;
    private final PromotionRepository promotionRepository;

    PricingAdminController(ProductPriceRepository priceRepository, PromotionRepository promotionRepository) {
        this.priceRepository = priceRepository;
        this.promotionRepository = promotionRepository;
    }

    public record SetPriceRequest(
            @NotNull(message = "productId est requis") UUID productId,
            @NotNull @Positive(message = "le prix doit être positif") BigDecimal unitPrice) {
    }

    public record PriceView(UUID productId, String unitPrice) {
        static PriceView from(ProductPrice p) {
            return new PriceView(p.getProductId(), p.unitPrice().toString());
        }
    }

    public record CreatePromotionRequest(
            @NotNull UUID productId, @NotNull @jakarta.validation.constraints.DecimalMin("0.0")
            @jakarta.validation.constraints.DecimalMax("100.0") BigDecimal discountPercent,
            @NotNull Instant validFrom, @NotNull Instant validUntil) {
    }

    public record PromotionView(UUID id, UUID productId, boolean active) {
        static PromotionView from(Promotion p) {
            return new PromotionView(p.getId(), p.getProductId(), p.isActive());
        }
    }

    /** Idempotent : crée le prix s'il n'existe pas, le met à jour sinon. */
    @PostMapping("/prices")
    public ResponseEntity<Map<String, UUID>> setPrice(@Valid @RequestBody SetPriceRequest request) {
        Money newPrice = Money.of(request.unitPrice().doubleValue());
        ProductPrice price = priceRepository.findByProductId(request.productId())
                .map(existing -> {
                    existing.updatePrice(newPrice);
                    return existing;
                })
                .orElseGet(() -> ProductPrice.of(request.productId(), newPrice));

        priceRepository.save(price);
        return ResponseEntity.ok(Map.of("productId", price.getProductId()));
    }

    @GetMapping("/prices")
    public ResponseEntity<List<PriceView>> listPrices() {
        List<PriceView> prices = priceRepository.findAll().stream()
                .map(PriceView::from)
                .toList();
        return ResponseEntity.ok(prices);
    }

    @GetMapping("/prices/{productId}")
    public ResponseEntity<PriceView> getPrice(@PathVariable UUID productId) {
        return priceRepository.findByProductId(productId)
                .map(PriceView::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/promotions")
    public ResponseEntity<Map<String, UUID>> createPromotion(@Valid @RequestBody CreatePromotionRequest request) {
        Promotion promotion = Promotion.percentageDiscount(
                request.productId(), request.discountPercent(), request.validFrom(), request.validUntil());
        promotionRepository.save(promotion);
        return ResponseEntity.ok(Map.of("promotionId", promotion.getId()));
    }

    @GetMapping("/promotions")
    public ResponseEntity<List<PromotionView>> listPromotions() {
        List<PromotionView> promotions = promotionRepository.findAll().stream()
                .map(PromotionView::from)
                .toList();
        return ResponseEntity.ok(promotions);
    }

    @GetMapping("/promotions/{productId}")
    public ResponseEntity<List<PromotionView>> getPromotions(@PathVariable UUID productId) {
        List<PromotionView> promotions = promotionRepository.findByProductId(productId).stream()
                .map(PromotionView::from)
                .toList();
        return ResponseEntity.ok(promotions);
    }

    @DeleteMapping("/prices/{productId}")
    public ResponseEntity<Void> deletePrice(@PathVariable UUID productId) {
        return priceRepository.findByProductId(productId)
                .map(price -> {
                    priceRepository.delete(price);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/promotions/{promotionId}")
    public ResponseEntity<Void> deactivatePromotion(@PathVariable UUID promotionId) {
        return promotionRepository.findById(promotionId)
                .map(promo -> {
                    promo.deactivate();
                    promotionRepository.save(promo);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
