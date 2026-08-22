/**
 * Pricing & Promotions module.
 *
 * Owns: ProductPrice, Promotion (aggregate root), PricingEngine (domain service).
 * Exposes a synchronous API (PricingEngine) called directly by pos -
 * a cashier can't wait on an async event round-trip for a price.
 * Publishes: PromotionActivated, PromotionExpired.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Pricing & Promotions"
)
package com.example.erp.pricing;