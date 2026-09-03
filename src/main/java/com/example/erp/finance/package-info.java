/**
 * Finance module.
 *
 * Owns the chart of accounts, balanced journal entries, supplier invoices,
 * supplier payments, and financial reporting. It consumes immutable events
 * from POS and Procurement; POS does not depend on Finance.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Finance",
        allowedDependencies = {"pos", "procurement", "shared"}
)
package com.example.erp.finance;
