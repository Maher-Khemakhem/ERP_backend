package com.example.erp.procurement;

import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderControllerTest {

    @Test
    void orderView_buildsTotalFromMoneyAmountWithoutCurrencySuffix() {
        Supplier supplier = Supplier.create("Acme", "+216 71 000 000", "acme@example.com");
        PurchaseOrder order = PurchaseOrder.draft(supplier.getId());
        order.addLine(UUID.randomUUID(), Quantity.each(3), Money.of(12.34));

        SupplierRepository supplierRepository = Mockito.mock(SupplierRepository.class);
        Mockito.when(supplierRepository.findById(supplier.getId())).thenReturn(Optional.of(supplier));

        PurchaseOrderController.OrderView view = PurchaseOrderController.OrderView.from(order, supplierRepository);

        assertThat(view.supplierName()).isEqualTo("Acme");
        assertThat(view.totalCost()).isEqualTo("37.020");
        assertThat(view.lines()).hasSize(1);
        assertThat(view.lines().getFirst().unitCost()).isEqualTo("12.340");
    }
}
