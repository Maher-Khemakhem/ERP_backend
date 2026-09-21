package com.example.erp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository Spring Data JPA pour Product.
 *
 * Package-private volontairement — même règle que StockLevelRepository :
 * rien en dehors du module Inventory ne doit accéder directement à la
 * persistance des produits. Les autres modules passent par les
 * événements ou par des méthodes publiques de InventoryService.
 */
interface ProductRepository extends JpaRepository<Product, UUID> {
    @Override
    @EntityGraph(attributePaths = "family")
    java.util.List<Product> findAll();

    @Override
    @EntityGraph(attributePaths = "family")
    Optional<Product> findById(UUID id);

    @EntityGraph(attributePaths = "family")
    Optional<Product> findBySku(String sku);
}
