/**
 * Finance module.
 *
 * Owns: LedgerEntry, accounting history.
 * This project keeps POS simplified and direct; Finance is no longer
 * driven by POS event listeners.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Finance"
)
package com.example.erp.finance;