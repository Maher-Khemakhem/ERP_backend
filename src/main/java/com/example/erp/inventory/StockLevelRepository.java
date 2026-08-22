package com.example.erp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository Spring Data JPA pour l'aggregate StockLevel.
 *
 * Package-private volontairement : rien en dehors du module Inventory
 * ne doit accéder directement à la persistance. Les autres modules
 * passent par les événements, jamais par ce repository.
 */
interface StockLevelRepository extends JpaRepository<StockLevel, UUID> {

    Optional<StockLevel> findByProductId(UUID productId);
}