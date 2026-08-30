package com.example.erp.procurement;

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

    AlertController(ProcurementService procurementService, LowStockAlertRepository alertRepository) {
        this.procurementService = procurementService;
        this.alertRepository = alertRepository;
    }

    public record AlertView(UUID id, UUID productId, String productName, int currentStock,
                            int reorderPoint, String createdAt, boolean acknowledged) {
        static AlertView from(LowStockAlert a) {
            return new AlertView(
                    a.getId(), a.getProductId(), null,
                    0, 0,
                    a.getRaisedAt().toString(),
                    a.isAcknowledged());
        }
    }

    @GetMapping
    public ResponseEntity<List<AlertView>> list() {
        List<AlertView> alerts = alertRepository.findAll().stream()
                .map(AlertView::from)
                .toList();
        return ResponseEntity.ok(alerts);
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<Map<String, UUID>> acknowledge(@PathVariable UUID id) {
        procurementService.acknowledgeAlert(id);
        return ResponseEntity.ok(Map.of("id", id));
    }
}
