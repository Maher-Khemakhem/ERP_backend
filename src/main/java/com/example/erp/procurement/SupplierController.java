package com.example.erp.procurement;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/procurement/suppliers")
class SupplierController {

    private final SupplierRepository supplierRepository;

    SupplierController(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    public record CreateSupplierRequest(String name, String contactEmail, String phone, String address) {
    }

    public record SupplierView(UUID id, String name, String contactEmail, String phone, String address) {
        static SupplierView from(Supplier s) {
            return new SupplierView(s.getId(), s.getName(), s.getContactEmail(), s.getContactPhone(), s.getAddress());
        }
    }

    @GetMapping
    public ResponseEntity<List<SupplierView>> list() {
        List<SupplierView> suppliers = supplierRepository.findAll().stream()
                .map(SupplierView::from)
                .toList();
        return ResponseEntity.ok(suppliers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupplierView> get(@PathVariable UUID id) {
        return supplierRepository.findById(id)
                .map(SupplierView::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> create(@RequestBody CreateSupplierRequest request) {
        Supplier supplier = Supplier.create(request.name(), request.phone(), request.contactEmail(), request.address());
        supplierRepository.save(supplier);
        return ResponseEntity.ok(Map.of("id", supplier.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, UUID>> update(@PathVariable UUID id, @RequestBody CreateSupplierRequest request) {
        return supplierRepository.findById(id)
                .map(supplier -> {
                    supplier.update(request.name(), request.phone(), request.contactEmail(), request.address());
                    supplierRepository.save(supplier);
                    return ResponseEntity.ok(Map.of("id", id));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (supplierRepository.existsById(id)) {
            supplierRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
