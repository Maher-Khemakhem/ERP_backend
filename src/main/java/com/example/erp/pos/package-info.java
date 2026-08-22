/**
 * POS module.
 *
 * Owns: Till, CashSession, PosTransaction (aggregate root), TransactionLine.
 * Publishes: TransactionCompleted, CashSessionOpened, CashSessionClosed.
 * Calls synchronously (not via events - latency-critical at checkout):
 *   pricing module's PricingEngine.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Point of Sale",
        allowedDependencies = {"pricing", "shared"}
)
package com.example.erp.pos;