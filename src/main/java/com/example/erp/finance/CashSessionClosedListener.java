package com.example.erp.finance;

import org.springframework.stereotype.Component;

/**
 * No event listener is used here anymore.
 * Cash reconciliation is handled directly in the POS service,
 * keeping the module simpler and easier to maintain.
 */
@Component
class CashSessionClosedListener {
    // left intentionally empty: the event-based flow was removed
}