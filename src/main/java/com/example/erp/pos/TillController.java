package com.example.erp.pos;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/pos/tills")
class TillController {

    private final TillRepository tillRepository;

    TillController(TillRepository tillRepository) {
        this.tillRepository = tillRepository;
    }

    public record CreateTillRequest(String tillNumber) {
    }

    public record TillView(UUID id, String tillNumber, String status) { 
        static TillView from(Till t) { 
            return new TillView(t.getId(), t.getTillNumber(), t.getStatus().name()); 
        };
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> create(@RequestBody CreateTillRequest request) {
        if (tillRepository.count() > 0) {
            throw new IllegalStateException("This store already has its single till");
        }
        Till till = Till.create(request.tillNumber());
        tillRepository.save(till);
        return ResponseEntity.ok(Map.of("id", till.getId()));
    }

    @GetMapping
    public ResponseEntity<List<TillView>> list() {
        List<TillView> tills = tillRepository.findAll().stream()
                .map(TillView::from)
                .toList();
        return ResponseEntity.ok(tills);
    }

    @GetMapping("/{tillId}")
    public ResponseEntity<TillView> get(@PathVariable UUID tillId) {
        return tillRepository.findById(tillId)
                .map(TillView::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{tillId}")
    public ResponseEntity<Map<String, UUID>> update(@PathVariable UUID tillId, @RequestBody CreateTillRequest request) {
        return tillRepository.findById(tillId)
                .map(till -> {
                    Till updated = Till.create(request.tillNumber());
                    try {
                        var field = Till.class.getDeclaredField("id");
                        field.setAccessible(true);
                        field.set(updated, tillId);
                        var statusField = Till.class.getDeclaredField("status");
                        statusField.setAccessible(true);
                        statusField.set(updated, till.getStatus());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    tillRepository.save(updated);
                    return ResponseEntity.ok(Map.of("id", tillId));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{tillId}")
    public ResponseEntity<Void> delete(@PathVariable UUID tillId) {
        if (tillRepository.existsById(tillId)) {
            tillRepository.deleteById(tillId);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
