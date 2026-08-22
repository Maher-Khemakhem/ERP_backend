package com.example.erp.pricing;

import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;

/**
 * PricingStrategy — calcule le prix d'une ligne à partir d'un prix
 * unitaire de référence et d'une quantité.
 *
 * Deux implémentations distinctes plutôt qu'un if/else dans
 * PricingEngine : un produit à l'unité (3 boîtes à 2,500 TND) et un
 * produit au poids (0,450 kg à 12,000 TND/kg) n'ont pas la même
 * arithmétique, et cette différence ne devrait jamais fuiter dans le
 * code appelant.
 */
public interface PricingStrategy {

    Money calculateLinePrice(Quantity quantity, Money unitPrice);

    /** Vrai si cette stratégie sait gérer ce type de quantité. */
    boolean supports(Quantity.UnitType unitType);
}