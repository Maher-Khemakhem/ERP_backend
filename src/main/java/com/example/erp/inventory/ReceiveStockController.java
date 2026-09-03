package com.example.erp.inventory;

import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotBlank;
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

    public record ReceiveStockRequest(@NotNull UUID productId, String lotNumber,
                                      @NotNull @Positive BigDecimal quantityValue, @NotBlank String unitType,
                                      LocalDate expiryDate, LocalDate receivedDate,
                                      String supplierReference, @PositiveOrZero BigDecimal unitCost) {
    }

    @PostMapping("/receive-stock")
    public ResponseEntity<Map<String, String>> receiveStock(@Valid @RequestBody ReceiveStockRequest request) {
        Quantity.UnitType unitType = Quantity.UnitType.valueOf(request.unitType());
        Quantity quantity = unitType == Quantity.UnitType.EACH
                ? Quantity.each(request.quantityValue().intValueExact())
                : new Quantity(request.quantityValue(), unitType);
        LocalDate receivedDate = request.receivedDate() == null ? LocalDate.now() : request.receivedDate();
        inventoryService.receiveStock(request.productId(), quantity, request.lotNumber(), request.expiryDate(),
                receivedDate, request.supplierReference(),
                request.unitCost() == null ? Money.zero() : Money.of(request.unitCost()));
        return ResponseEntity.ok(Map.of("status", "received"));
    }
}
