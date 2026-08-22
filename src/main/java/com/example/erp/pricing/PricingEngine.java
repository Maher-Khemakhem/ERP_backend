package com.example.erp.pricing;

import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * PricingEngine — domain service, point d'entrée PUBLIC et
 * SYNCHRONE du module Pricing.
 *
 * C'est la seule classe de ce module que POS a le droit d'appeler
 * directement (cf. package-info.java de pos : allowedDependencies =
 * {"pricing"}). Tout le reste du module (Promotion, PricingStrategy,
 * ProductPriceRepository) reste interne.
 *
 * Rôle : étant donné un produit, une quantité, et le prix de base,
 * calculer le prix final de la ligne — base price × stratégie de
 * quantité (unité/poids), puis remise éventuellement active.
 */
@Service
public class PricingEngine {

    private final ProductPriceRepository priceRepository;
    private final PromotionRepository promotionRepository;
    private final List<PricingStrategy> strategies;

    PricingEngine(ProductPriceRepository priceRepository,
                  PromotionRepository promotionRepository,
                  List<PricingStrategy> strategies) {
        this.priceRepository = priceRepository;
        this.promotionRepository = promotionRepository;
        this.strategies = strategies;
    }

    /**
     * Calcule le prix final d'une ligne de vente.
     *
     * @param productId identifiant du produit scanné
     * @param quantity  quantité (unités ou poids)
     * @return le prix final, remise(s) éligible(s) déjà appliquée(s)
     */
    public Money calculateLinePrice(UUID productId, Quantity quantity) {
        Money basePrice = priceRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Aucun prix défini pour le produit : " + productId))
                .unitPrice();

        PricingStrategy strategy = strategyFor(quantity.unitType());
        Money lineTotal = strategy.calculateLinePrice(quantity, basePrice);

        return applyActivePromotion(productId, lineTotal);
    }

    private PricingStrategy strategyFor(Quantity.UnitType unitType) {
        return strategies.stream()
                .filter(s -> s.supports(unitType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Aucune stratégie de tarification pour le type : " + unitType));
    }

    private Money applyActivePromotion(UUID productId, Money price) {
        Instant now = Instant.now();
        return promotionRepository.findByProductId(productId).stream()
                .filter(promo -> promo.appliesTo(productId, now))
                .findFirst()
                .map(promo -> promo.applyTo(price))
                .orElse(price);
    }
}