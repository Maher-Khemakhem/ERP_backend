package com.example.erp.procurement;

import com.example.erp.shared.Money;
import com.example.erp.shared.Quantity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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

    public record CreateOrderRequest(UUID supplierId, List<OrderLineRequest> lines) {
    }

    public record OrderLineRequest(UUID productId, int quantity, BigDecimal unitCost) {
    }

    public record OrderLineView(UUID id, UUID productId, int quantity, String unitCost) {
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
                                l.getQuantity().value().intValue(), l.getUnitCost().toString()))
                        .toList()
                    : List.of();
            BigDecimal total = lineViews.stream()
                    .map(l -> new BigDecimal(l.unitCost()).multiply(BigDecimal.valueOf(l.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new OrderView(
                    o.getId(), o.getSupplierId(), supplierName,
                    o.getStatus().name(), lineViews, total.toString(),
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
    public ResponseEntity<Map<String, UUID>> create(@RequestBody CreateOrderRequest request) {
        UUID orderId = procurementService.createDraftSuggestion(
                request.supplierId(),
                request.lines().get(0).productId(),
                Quantity.each(request.lines().get(0).quantity()),
                Money.of(request.lines().get(0).unitCost().doubleValue()));
        return ResponseEntity.ok(Map.of("id", orderId));
    }

    @PostMapping("/{id}/place")
    public ResponseEntity<Map<String, UUID>> place(@PathVariable UUID id) {
        procurementService.placeOrder(id);
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PostMapping("/{id}/receive")
    public ResponseEntity<Map<String, UUID>> receive(@PathVariable UUID id) {
        procurementService.receiveGoods(id);
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, UUID>> cancel(@PathVariable UUID id) {
        PurchaseOrder order = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Commande introuvable : " + id));
        // Mark as cancelled by saving with appropriate status
        // For now, we just return the id - full cancel logic can be added later
        return ResponseEntity.ok(Map.of("id", id));
    }
}
