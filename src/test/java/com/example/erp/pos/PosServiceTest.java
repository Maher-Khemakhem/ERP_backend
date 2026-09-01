package com.example.erp.pos;

import com.example.erp.inventory.InventoryService;
import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosServiceTest {

    @Mock
    private TillRepository tillRepository;

    @Mock
    private CashSessionRepository cashSessionRepository;

    @Mock
    private PosTransactionRepository transactionRepository;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private PosService posService;

    @Test
    void createTill_allowsMultipleTills() {
        when(tillRepository.count()).thenReturn(0L, 1L);

        assertThatCode(() -> {
            posService.createTill("Till 1");
            posService.createTill("Till 2");
        }).doesNotThrowAnyException();
    }

    @Test
    void completeTransaction_deductsEveryLineThroughInventory() {
        UUID cashSessionId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID secondProductId = UUID.randomUUID();
        PosTransaction transaction = PosTransaction.start(cashSessionId);
        transaction.addLine(productId, Quantity.each(2), Money.of(6));
        transaction.addLine(secondProductId, Quantity.kg(0.5), Money.of(3));

        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        when(cashSessionRepository.findById(cashSessionId))
                .thenReturn(Optional.of(CashSession.open(cashSessionId, "Cashier", Money.zero())));

        posService.completeTransaction(transaction.getId(), PosTransaction.PaymentMethod.CARD);

        verify(inventoryService).sell(productId, Quantity.each(2));
        verify(inventoryService).sell(secondProductId, Quantity.kg(0.5));
        verify(transactionRepository).save(transaction);
        assertThat(transaction.getStatus()).isEqualTo(PosTransaction.TransactionStatus.COMPLETED);
    }

    @Test
    void completeTransaction_doesNotCompleteWhenInventoryRejectsALine() {
        UUID cashSessionId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        PosTransaction transaction = PosTransaction.start(cashSessionId);
        transaction.addLine(productId, Quantity.each(2), Money.of(6));

        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        when(cashSessionRepository.findById(cashSessionId))
                .thenReturn(Optional.of(CashSession.open(cashSessionId, "Cashier", Money.zero())));
        doThrow(new StockLevel.InsufficientStockException(
                productId, Quantity.each(2), Quantity.each(1)))
                .when(inventoryService).sell(productId, Quantity.each(2));

        assertThatThrownBy(() -> posService.completeTransaction(
                transaction.getId(), PosTransaction.PaymentMethod.CARD))
                .isInstanceOf(StockLevel.InsufficientStockException.class);

        verify(transactionRepository, never()).save(transaction);
        assertThat(transaction.getStatus()).isEqualTo(PosTransaction.TransactionStatus.IN_PROGRESS);
    }

    @Test
    void completeTransaction_rejectsClosedCashSession() {
        UUID cashSessionId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        PosTransaction transaction = PosTransaction.start(cashSessionId);
        transaction.addLine(productId, Quantity.each(1), Money.of(2));
        CashSession session = CashSession.open(cashSessionId, "Cashier", Money.zero());
        session.close(Money.zero());

        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        when(cashSessionRepository.findById(cashSessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> posService.completeTransaction(
                transaction.getId(), PosTransaction.PaymentMethod.CARD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not open");

        verify(inventoryService, never()).sell(productId, Quantity.each(1));
        verify(transactionRepository, never()).save(transaction);
    }

    @Test
    void addLine_validatesProductUnitThroughInventory() {
        UUID cashSessionId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        PosTransaction transaction = PosTransaction.start(cashSessionId);

        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        when(cashSessionRepository.findById(cashSessionId))
                .thenReturn(Optional.of(CashSession.open(cashSessionId, "Cashier", Money.zero())));
        doThrow(new IllegalArgumentException("Requested unit type does not match the product"))
                .when(inventoryService).validateProductForSale(productId, Quantity.UnitType.WEIGHT);

        assertThatThrownBy(() -> posService.addLine(
                transaction.getId(), productId, Quantity.kg(0.5), Money.of(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unit type");

        verify(transactionRepository, never()).save(transaction);
    }
}
