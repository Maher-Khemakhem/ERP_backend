package com.example.erp.pos;

import com.example.erp.inventory.InventoryService;
import com.example.erp.pricing.PricingEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScannerSessionServiceTest {

    @Mock
    private PosService posService;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private PricingEngine pricingEngine;

    @Test
    void create_returnsShortLivedPairingForOpenTicket() {
        PosTransaction transaction = PosTransaction.start(UUID.randomUUID());
        when(posService.getTransaction(transaction.getId())).thenReturn(transaction);

        ScannerSessionService service = new ScannerSessionService(posService, inventoryService, pricingEngine);
        ScannerSessionService.Pairing pairing = service.create(transaction.getId());

        assertThat(pairing.sessionId()).isNotNull();
        assertThat(pairing.token()).isNotBlank();
        assertThat(pairing.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void scan_rejectsWrongPairingTokenBeforeResolvingProduct() {
        PosTransaction transaction = PosTransaction.start(UUID.randomUUID());
        when(posService.getTransaction(transaction.getId())).thenReturn(transaction);
        ScannerSessionService service = new ScannerSessionService(posService, inventoryService, pricingEngine);
        ScannerSessionService.Pairing pairing = service.create(transaction.getId());

        assertThatThrownBy(() -> service.scan(pairing.sessionId(), "wrong-token", "012345"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid scanner pairing token");
        verify(inventoryService, never()).findProductBySku("012345");
    }
}
