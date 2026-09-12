# Class Diagram — `pricing` Module

The `pricing` module owns product base prices and time-bounded percentage promotions.  
`PricingEngine` is the **only public API** consumed by other modules (specifically POS).

```mermaid
classDiagram

    %% ── Entities ───────────────────────────────────────────────
    class ProductPrice {
        <<Entity — table: pricing.product_price>>
        -UUID id PK
        -UUID productId UK
        -Money unitPrice
        +of(productId, unitPrice) ProductPrice$
        +updatePrice(Money) void
    }
    note for ProductPrice "One price per product (unique constraint on productId).\nPrice writes are effectively upserts — PricingEngine creates or replaces.\nPrice cannot be negative."

    class Promotion {
        <<Entity — table: pricing.promotion>>
        -UUID id PK
        -UUID productId
        -PromotionType type
        -BigDecimal discountPercent
        -Instant validFrom
        -Instant validUntil
        -boolean active
        +percentageDiscount(productId, discountPercent, validFrom, validUntil) Promotion$
        +appliesTo(productId, now) boolean
        +applyTo(price) Money
        +deactivate() void
    }
    note for Promotion "discountPercent must be in [0, 100].\nvalidUntil must be after validFrom.\nOnly the first currently active promotion for a product is applied (no stacking)."

    %% ── Enum ───────────────────────────────────────────────────
    class PromotionType {
        <<enumeration>>
        PERCENTAGE_DISCOUNT
    }
    note for PromotionType "BUNDLE, BOGO etc. reserved for future use."

    %% ── Pricing Strategy ───────────────────────────────────────
    class PricingStrategy {
        <<interface>>
        +calculate(basePrice, quantity) Money
    }

    class PerUnitPricing {
        <<Strategy — for UnitType.EACH>>
        +calculate(basePrice, quantity) Money
    }
    note for PerUnitPricing "result = basePrice × quantity (whole units)"

    class PerWeightPricing {
        <<Strategy — for UnitType.WEIGHT>>
        +calculate(basePrice, quantity) Money
    }
    note for PerWeightPricing "result = basePrice × weight in kg (3 dp)"

    %% ── Engine ─────────────────────────────────────────────────
    class PricingEngine {
        <<Service — PUBLIC MODULE API>>
        -ProductPriceRepository priceRepository
        -PromotionRepository promotionRepository
        +calculatePrice(productId, unitType, quantity) Money
        +upsertPrice(productId, unitPrice) ProductPrice
        +deletePrice(productId) void
        +createPromotion(productId, discountPercent, validFrom, validUntil) Promotion
        +deactivatePromotion(promotionId) void
    }
    note for PricingEngine "Used by PosService to price every transaction line.\ncalculatePrice: selects strategy by unitType → applies base price → applies first active promotion."

    %% ── Relationships ──────────────────────────────────────────
    Promotion --> PromotionType : type
    PricingStrategy <|.. PerUnitPricing : implements
    PricingStrategy <|.. PerWeightPricing : implements
    PricingEngine --> ProductPrice : reads / writes
    PricingEngine --> Promotion : reads / writes
    PricingEngine --> PricingStrategy : selects by UnitType
```

## Pricing calculation flow

```
PricingEngine.calculatePrice(productId, unitType, quantity)
  │
  ├─ 1. Load ProductPrice → basePrice (Money)
  ├─ 2. Select strategy: UnitType.EACH → PerUnitPricing
  │                       UnitType.WEIGHT → PerWeightPricing
  ├─ 3. linePrice = strategy.calculate(basePrice, quantity)
  └─ 4. Find first active Promotion for productId at Instant.now()
           → if found: linePrice = promotion.applyTo(linePrice)
           → return linePrice (VAT-exclusive)
```

## Key constraints

| Constraint | Detail |
|---|---|
| One price per product | `productId` has a `UNIQUE` constraint on `product_price` |
| Non-negative price | `ProductPrice.of()` rejects negative `unitPrice` |
| Valid promotion window | `validUntil` must be strictly after `validFrom` |
| Single promotion | Only the **first** currently active promotion is applied; promotions do not stack |
| No price → error | `calculatePrice()` throws if no `ProductPrice` exists for the product |
