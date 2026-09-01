/**
 * Inventory module.
 *
 * Owns: Product, Lot, StockLevel, stock movements.
 * Exposes InventoryService as the application boundary for stock movements.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Inventory"
)
package com.example.erp.inventory;
