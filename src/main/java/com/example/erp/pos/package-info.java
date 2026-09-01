/**
 * POS module.
 *
 * Owns: Till, CashSession, PosTransaction, TransactionLine.
 * Calls InventoryService to deduct stock when a sale is completed and calls
 * the pricing engine synchronously before adding a line.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Point of Sale",
        allowedDependencies = {"inventory", "pricing", "shared"}
)
package com.example.erp.pos;
