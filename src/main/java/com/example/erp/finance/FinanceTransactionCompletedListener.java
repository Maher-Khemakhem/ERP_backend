package com.example.erp.finance;

import org.springframework.stereotype.Component;

/**
 * This module no longer listens to POS events.
 * POS now updates the cashier flow directly in the service layer,
 * so Finance remains independent and simpler.
 */
@Component
class FinanceTransactionCompletedListener {
    // intentionally left empty: event-based sales recording was removed
}