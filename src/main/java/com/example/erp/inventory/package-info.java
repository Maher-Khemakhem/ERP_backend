/**
 * Inventory module.
 *
 * Owns: Product, Lot, StockLevel (aggregate root), StockMovement.
 * Publishes: StockSold, StockReceived, StockWrittenOff, LowStockThresholdReached.
 * Listens to: TransactionCompleted (from pos), GoodsReceived (from procurement).
 *
 * Nothing outside this package may import from inventory.internal.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Inventory"
)
package com.example.erp.inventory;