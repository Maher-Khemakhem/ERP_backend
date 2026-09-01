/**
 * Procurement module.
 *
 * Owns: Supplier, PurchaseOrder (aggregate root), GoodsReceipt.
 * Uses InventoryService to create stock lots when goods are received.
 * Publishes: PurchaseOrderPlaced, GoodsReceived.
 * Listens to: LowStockThresholdReached (from inventory) to suggest reorders.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Procurement",
        allowedDependencies = {"inventory", "shared"}
)
package com.example.erp.procurement;
