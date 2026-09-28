package com.example.erp.ecommerce;

import com.example.erp.inventory.InventoryCatalog;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OnlineOrderTest {

    @Test
    void paymentConfirmationMovesPendingOrderToConfirmedAndIsIdempotent() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        InventoryCatalog.CatalogProduct product = new InventoryCatalog.CatalogProduct(
                productId, "COFFEE-1", "Coffee", null, null, null, null,
                Quantity.UnitType.EACH, BigDecimal.valueOf(19), BigDecimal.ONE);
        StorefrontCatalogService.LineQuote quote = new StorefrontCatalogService.LineQuote(
                product, Quantity.each(1), Money.of(10), Money.of(10), Money.of(1.900));
        OnlineOrder order = OnlineOrder.create(
                customerId,
                List.of(OnlineOrderLine.from(quote)),
                Money.of(10),
                Money.of(1.900),
                Money.of(11.900));

        order.confirmPayment("MOCK-CARD-1");
        order.confirmPayment("MOCK-CARD-2");

        assertThat(order.getStatus()).isEqualTo(OnlineOrderStatus.CONFIRMED);
        assertThat(order.getPaymentReference()).isEqualTo("MOCK-CARD-1");
    }

}
