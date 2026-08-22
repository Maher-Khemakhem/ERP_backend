package com.example.erp.pricing;

import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.springframework.stereotype.Component;

/** Produits vendus au poids (ex. 0,450 kg de tomates à 3,200 TND/kg). */
@Component
class PerWeightPricing implements PricingStrategy {

    @Override
    public Money calculateLinePrice(Quantity quantity, Money unitPrice) {
        if (!supports(quantity.unitType())) {
            throw new IllegalArgumentException("PerWeightPricing ne gère que les quantités WEIGHT");
        }
        // unitPrice est le prix par kg ; quantity.value() est en kg
        return unitPrice.multiply(quantity.value());
    }

    @Override
    public boolean supports(Quantity.UnitType unitType) {
        return unitType == Quantity.UnitType.WEIGHT;
    }
}