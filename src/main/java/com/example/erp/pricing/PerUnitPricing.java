package com.example.erp.pricing;

import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

/** Produits vendus à l'unité (ex. 3 boîtes de conserve à 2,500 TND/unité). */
@Component
class PerUnitPricing implements PricingStrategy {

    @Override
    public Money calculateLinePrice(Quantity quantity, Money unitPrice) {
        if (!supports(quantity.unitType())) {
            throw new IllegalArgumentException("PerUnitPricing ne gère que les quantités EACH");
        }
        int count = quantity.value().intValueExact();
        return unitPrice.multiply(count);
    }

    @Override
    public boolean supports(Quantity.UnitType unitType) {
        return unitType == Quantity.UnitType.EACH;
    }
}