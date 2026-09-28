package com.example.erp.inventory;

import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReservedStockSaleTest {

    @Test
    void reservedQuantityIsConvertedIntoSoldStock() {
        UUID productId = UUID.randomUUID();
        StockLevel stock = StockLevel.initialize(productId);
        stock.receive(Lot.receive("ONLINE-1", productId, null, LocalDate.now(), "test", Money.of(2.500)),
                Quantity.each(1));

        stock.reserve(Quantity.each(1));
        Money cost = stock.sellReserved(Quantity.each(1), LocalDate.now(), 0);

        assertThat(cost).isEqualTo(Money.of(2.500));
        assertThat(stock.getLots().getFirst().onHand()).isEqualTo(Quantity.each(0));
        assertThat(stock.getLots().getFirst().reserved()).isEqualTo(Quantity.each(0));
    }
}
