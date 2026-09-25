package com.example.erp.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Lot quantity changes do not dirty the StockLevel row. Force its version
     * forward so every inventory mutation participates in the same conflict
     * check, including reservations that change only a child LotEntry.
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select s from StockLevel s where s.productId = :productId")
    Optional<StockLevel> findForMutationByProductId(@Param("productId") UUID productId);
}
