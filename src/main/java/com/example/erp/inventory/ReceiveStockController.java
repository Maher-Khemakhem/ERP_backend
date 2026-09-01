package com.example.erp.inventory;

import com.example.erp.shared.Quantity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
class ReceiveStockController {

    private final InventoryService inventoryService;

    ReceiveStockController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * quantityValue/unitType suivent la même convention que AddLineRequest
     * côté POS. expiryDate est optionnel (null pour un produit non
     * périssable) ; receivedDate optionnel, défaut = aujourd'hui.
     */
    public record ReceiveStockRequest(
            UUID productId,
            String lotNumber,
            BigDecimal quantityValue,
            String unitType,
            LocalDate expiryDate,
            LocalDate receivedDate,
            String supplierReference) {
    }

    @PostMapping("/receive-stock")
    public ResponseEntity<Map<String, String>> receiveStock(@RequestBody ReceiveStockRequest request) {
        Quantity.UnitType unitType = Quantity.UnitType.valueOf(request.unitType());
        Quantity quantity = unitType == Quantity.UnitType.EACH
                ? Quantity.each(request.quantityValue().intValueExact())
                : new Quantity(request.quantityValue(), unitType);

        LocalDate receivedDate = request.receivedDate() != null ? request.receivedDate() : LocalDate.now();

        inventoryService.receiveStock(
                request.productId(), quantity, request.lotNumber(), request.expiryDate(),
                receivedDate, request.supplierReference());

        return ResponseEntity.ok(Map.of("status", "received"));
    }
}
