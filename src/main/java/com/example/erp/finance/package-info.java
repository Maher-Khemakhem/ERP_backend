/**
 * Finance module.
 *
 * Owns: LedgerEntry, TillReconciliation.
 * Listens to: TransactionCompleted (revenue), GoodsReceived (AP),
 *             CashSessionClosed (till reconciliation).
 * Publishes nothing (yet) - Finance is mostly a consumer/system of record.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Finance"
)
package com.example.erp.finance;