package com.example.erp.ecommerce;

import com.example.erp.inventory.InventoryCatalog;
import com.example.erp.pricing.PricingEngine;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StorefrontCatalogServiceTest {

    @Test
    void catalogUsesInventoryAvailabilityAndSharedPricing() {
        UUID productId = UUID.randomUUID();
        InventoryCatalog inventoryCatalog = Mockito.mock(InventoryCatalog.class);
        PricingEngine pricingEngine = Mockito.mock(PricingEngine.class);
        InventoryCatalog.CatalogProduct product = new InventoryCatalog.CatalogProduct(
                productId, "MILK-1", "Milk", null, null, null, null,
                Quantity.UnitType.EACH, BigDecimal.valueOf(19), BigDecimal.valueOf(3));

        Mockito.when(inventoryCatalog.findOnlineProducts(null, null)).thenReturn(List.of(product));
        Mockito.when(pricingEngine.findUnitPrice(productId, Quantity.UnitType.EACH))
                .thenReturn(Optional.of(Money.of(2.500)));

        StorefrontCatalogService service = new StorefrontCatalogService(inventoryCatalog, pricingEngine);

        StorefrontCatalogService.StorefrontProduct result = service.list(null, null).getFirst();

        assertThat(result.unitPrice()).isEqualTo(Money.of(2.500));
        assertThat(result.availableQuantity()).isEqualByComparingTo("3");
        assertThat(result.inStock()).isTrue();
    }

    @Test
    void catalogOmitsProductsWithoutASellingPrice() {
        UUID productId = UUID.randomUUID();
        InventoryCatalog inventoryCatalog = Mockito.mock(InventoryCatalog.class);
        PricingEngine pricingEngine = Mockito.mock(PricingEngine.class);
        InventoryCatalog.CatalogProduct product = new InventoryCatalog.CatalogProduct(
                productId, "NO-PRICE", "Unpriced", null, null, null, null,
                Quantity.UnitType.EACH, BigDecimal.valueOf(19), BigDecimal.ZERO);

        Mockito.when(inventoryCatalog.findOnlineProducts("milk", null)).thenReturn(List.of(product));
        Mockito.when(pricingEngine.findUnitPrice(productId, Quantity.UnitType.EACH))
                .thenReturn(Optional.empty());

        StorefrontCatalogService service = new StorefrontCatalogService(inventoryCatalog, pricingEngine);

        assertThat(service.list("milk", null)).isEmpty();
    }
}
