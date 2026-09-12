package com.example.erp.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "product_family", schema = "inventory")
public class ProductFamily {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 160)
    private String name;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    protected ProductFamily() {
    }

    private ProductFamily(UUID id, String name, String imageUrl) {
        this.id = id;
        this.name = requireName(name);
        this.imageUrl = normalizeOptional(imageUrl);
    }

    public static ProductFamily create(String name, String imageUrl) {
        return new ProductFamily(UUID.randomUUID(), name, imageUrl);
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Family name cannot be empty");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getImageUrl() { return imageUrl; }
}