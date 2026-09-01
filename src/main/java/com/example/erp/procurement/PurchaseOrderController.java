package com.example.erp.procurement;

import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/procurement/orders")
class PurchaseOrderController {

    private final ProcurementService procurementService;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;

    PurchaseOrderController(ProcurementService procurementService,
                            PurchaseOrderRepository purchaseOrderRepository,
                            SupplierRepository supplierRepository) {
        this.procurementService = procurementService;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierRepository = supplierRepository;
    }

    public record CreateOrderRequest(
            @NotNull(message = "supplierId is required") UUID supplierId,
            @NotEmpty(message = "at least one order line is required")
            List<@Valid OrderLineRequest> lines) {
    }

    public record OrderLineRequest(
            @NotNull(message = "productId is required") UUID productId,
            @NotNull(message = "quantityValue is required")
            @Positive(message = "quantityValue must be greater than zero") BigDecimal quantityValue,
            @NotBlank(message = "unitType is required") String unitType,
            @NotNull(message = "unitCost is required")
            @PositiveOrZero(message = "unitCost cannot be negative") BigDecimal unitCost) {
    }

    public record ReceiveOrderRequest(
            @NotEmpty(message = "at least one receipt line is required")
            List<@Valid ReceiveLineRequest> lines) {
    }

    public record ReceiveLineRequest(
            @NotNull(message = "purchaseOrderLineId is required") UUID purchaseOrderLineId,
            @NotNull(message = "quantityValue is required")
            @Positive(message = "quantityValue must be greater than zero") BigDecimal quantityValue,
            @NotBlank(message = "unitType is required") String unitType,
            String lotNumber,
            LocalDate expiryDate,
            LocalDate receivedDate,
            String supplierReference) {
    }

    public record OrderLineView(UUID id, UUID productId, String quantity,
                                String unitType, String unitCost) {
    }

    public record OrderView(UUID id, UUID supplierId, String supplierName, String status,
                            List<OrderLineView> lines, String totalCost,
                            String createdAt, String placedAt, String receivedAt) {
        static OrderView from(PurchaseOrder o, SupplierRepository supplierRepo) {
            String supplierName = supplierRepo.findById(o.getSupplierId())
                    .map(Supplier::getName)
                    .orElse(null);
            List<OrderLineView> lineViews = o.getLines() != null
                    ? o.getLines().stream()
                    .map(l -> new OrderLineView(l.getId(), l.getProductId(),
                            l.getQuantity().value().toPlainString(),
                            l.getQuantity().unitType().name(),
                            l.getUnitCost().amount().toPlainString()))
                    .toList()
                    : List.of();
            BigDecimal total = o.getLines().stream()
                    .map(l -> l.getUnitCost().amount().multiply(l.getQuantity().value()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new OrderView(
                    o.getId(), o.getSupplierId(), supplierName,
                    o.getStatus().name(), lineViews, total.toPlainString(),
                    null, o.getPlacedAt() != null ? o.getPlacedAt().toString() : null, null);
        }
    }

    @GetMapping
    public ResponseEntity<List<OrderView>> list() {
        List<OrderView> orders = purchaseOrderRepository.findAll().stream()
                .map(o -> OrderView.from(o, supplierRepository))
                .toList();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderView> get(@PathVariable UUID id) {
        return purchaseOrderRepository.findById(id)
                .map(o -> OrderView.from(o, supplierRepository))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> create(@Valid @RequestBody CreateOrderRequest request) {
        List<ProcurementService.OrderLineInput> lines = request.lines().stream()
                .map(line -> new ProcurementService.OrderLineInput(
                        line.productId(), toQuantity(line.quantityValue(), line.unitType()),
                        Money.of(line.unitCost().doubleValue())))
                .toList();
        UUID orderId = procurementService.createDraft(request.supplierId(), lines);
        return ResponseEntity.ok(Map.of("id", orderId));
    }

    @PostMapping("/{id}/place")
    public ResponseEntity<Map<String, UUID>> place(@PathVariable UUID id) {
        procurementService.placeOrder(id);
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, UUID>> cancel(@PathVariable UUID id) {
        procurementService.cancelOrder(id);
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PostMapping("/{id}/receive")
    public ResponseEntity<Map<String, UUID>> receive(@PathVariable UUID id,
                                                     @Valid @RequestBody ReceiveOrderRequest request) {
        List<ProcurementService.ReceiptInput> lines = request.lines().stream()
                .map(line -> new ProcurementService.ReceiptInput(
                        line.purchaseOrderLineId(), toQuantity(line.quantityValue(), line.unitType()),
                        line.lotNumber(), line.expiryDate(), line.receivedDate(), line.supplierReference()))
                .toList();
        procurementService.receiveGoods(id, lines);
        return ResponseEntity.ok(Map.of("id", id));
    }

    private static Quantity toQuantity(BigDecimal value, String unitType) {
        Quantity.UnitType type = Quantity.UnitType.valueOf(unitType);
        if (type == Quantity.UnitType.EACH && value.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("EACH quantity must be a whole number");
        }
        return type == Quantity.UnitType.EACH
                ? Quantity.each(value.intValueExact())
                : new Quantity(value, type);
    }
}
