package com.example.erp.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/product-families")
class ProductFamilyController {
    private final ProductFamilyRepository familyRepository;

    ProductFamilyController(ProductFamilyRepository familyRepository) {
        this.familyRepository = familyRepository;
    }

    public record CreateFamilyRequest(@NotBlank String name, @Size(max = 500) String imageUrl) {}
    public record FamilyView(UUID id, String name, String imageUrl) {
        static FamilyView from(ProductFamily family) {
            return new FamilyView(family.getId(), family.getName(), family.getImageUrl());
        }
    }

    @GetMapping
    List<FamilyView> list() {
        return familyRepository.findAll().stream().map(FamilyView::from).toList();
    }

    @PostMapping
    ResponseEntity<Map<String, UUID>> create(@Valid @RequestBody CreateFamilyRequest request) {
        if (familyRepository.existsByNameIgnoreCase(request.name().trim())) {
            return ResponseEntity.status(409).build();
        }
        ProductFamily family = familyRepository.save(ProductFamily.create(request.name(), request.imageUrl()));
        return ResponseEntity.ok(Map.of("familyId", family.getId()));
    }
}