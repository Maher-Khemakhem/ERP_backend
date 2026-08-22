/**
 * Procurement module.
 *
 * Owns: Supplier, PurchaseOrder (aggregate root), GoodsReceipt.
 * Publishes: PurchaseOrderPlaced, GoodsReceived.
 * Listens to: LowStockThresholdReached (from inventory) to suggest reorders.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Procurement"
)
package com.example.erp.procurement;