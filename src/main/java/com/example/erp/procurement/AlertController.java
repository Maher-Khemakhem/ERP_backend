package com.example.erp.procurement;

import com.example.erp.inventory.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/procurement/alerts")
class AlertController {

    private final ProcurementService procurementService;
    private final LowStockAlertRepository alertRepository;
    private final InventoryService inventoryService;

    AlertController(ProcurementService procurementService,
                    LowStockAlertRepository alertRepository,
                    InventoryService inventoryService) {
        this.procurementService = procurementService;
        this.alertRepository = alertRepository;
        this.inventoryService = inventoryService;
    }

    public record AlertView(UUID id, UUID productId, String productName, double currentStock,
                            int reorderPoint, String createdAt, boolean acknowledged) {
    }

    private AlertView toView(LowStockAlert alert) {
        UUID productId = alert.getProductId();

        String productName = inventoryService.findProductById(productId)
                .map(p -> p.getName())
                .orElse("Unknown product");

        int reorderPoint = inventoryService.findProductById(productId)
                .map(p -> p.getReorderPoint())
                .orElse(0);

        double currentStock = inventoryService.totalOnHandQuantity(productId);

        return new AlertView(
                alert.getId(), productId, productName,
                currentStock, reorderPoint,
                alert.getRaisedAt().toString(),
                alert.isAcknowledged());
    }

    @GetMapping
    public ResponseEntity<List<AlertView>> list() {
        List<AlertView> alerts = alertRepository.findAll().stream()
                .map(this::toView)
                .toList();
        return ResponseEntity.ok(alerts);
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<Map<String, UUID>> acknowledge(@PathVariable UUID id) {
        procurementService.acknowledgeAlert(id);
        return ResponseEntity.ok(Map.of("id", id));
    }
}
