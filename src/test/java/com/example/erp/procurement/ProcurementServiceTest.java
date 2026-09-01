package com.example.erp.procurement;

import com.example.erp.inventory.InventoryService;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcurementServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private LowStockAlertRepository alertRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher events;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private ProcurementService procurementService;

    @Test
    void receiveGoods_createsInventoryLotAndMarksOrderReceived() {
        UUID supplierId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        PurchaseOrder order = PurchaseOrder.draft(supplierId);
        order.addLine(productId, Quantity.each(4), Money.of(2.5));
        order.place();
        order.clearDomainEvents();

        UUID lineId = order.getLines().getFirst().getId();
        when(purchaseOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        LocalDate expiryDate = LocalDate.now().plusDays(10);
        LocalDate receivedDate = LocalDate.now();
        procurementService.receiveGoods(order.getId(), List.of(new ProcurementService.ReceiptInput(
                lineId, Quantity.each(4), "LOT-001", expiryDate, receivedDate, "DELIVERY-001")));

        verify(inventoryService).receiveStock(
                productId, Quantity.each(4), "LOT-001", expiryDate, receivedDate, "DELIVERY-001");
        verify(purchaseOrderRepository).save(order);
        assertThat(order.getStatus()).isEqualTo(PurchaseOrder.PurchaseOrderStatus.RECEIVED);
        assertThat(order.domainEvents()).hasSize(0);
    }

    @Test
    void cancelOrder_cancelsDraftOrder() {
        PurchaseOrder order = PurchaseOrder.draft(UUID.randomUUID());
        when(purchaseOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        procurementService.cancelOrder(order.getId());

        assertThat(order.getStatus()).isEqualTo(PurchaseOrder.PurchaseOrderStatus.CANCELLED);
        verify(purchaseOrderRepository).save(order);
    }

    @Test
    void cancelOrder_rejectsReceivedOrder() {
        UUID productId = UUID.randomUUID();
        PurchaseOrder order = PurchaseOrder.draft(UUID.randomUUID());
        order.addLine(productId, Quantity.each(1), Money.of(1));
        order.place();
        order.clearDomainEvents();
        PurchaseOrderLine line = order.getLines().getFirst();
        order.markReceived(List.of(new GoodsReceived.ReceiptLine(
                line.getId(), productId, Quantity.each(1), Money.of(1),
                "LOT-001", null, null, null)));
        order.clearDomainEvents();
        when(purchaseOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> procurementService.cancelOrder(order.getId()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(order.getStatus()).isEqualTo(PurchaseOrder.PurchaseOrderStatus.RECEIVED);
    }
}
