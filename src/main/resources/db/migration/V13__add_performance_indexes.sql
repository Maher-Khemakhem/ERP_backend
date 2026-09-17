-- Performance indexes on high-frequency FK lookup columns.
-- PostgreSQL only auto-indexes PRIMARY KEY columns; foreign key UUID columns
-- used in WHERE clauses need explicit indexes or they cause full table scans.

-- POS: find all transactions for a session
CREATE INDEX IF NOT EXISTS idx_pos_transaction_cash_session_id
    ON pos.pos_transaction (cash_session_id);

-- POS: find all lines for a transaction (used on every ticket load)
CREATE INDEX IF NOT EXISTS idx_transaction_line_transaction_id
    ON pos.transaction_line (transaction_id);

-- Inventory: find lot entries for a stock level (used on every stock operation)
CREATE INDEX IF NOT EXISTS idx_lot_entry_stock_level_id
    ON inventory.lot_entry (stock_level_id);

-- Procurement: find alerts for a product (used when listing / deduplicating alerts)
CREATE INDEX IF NOT EXISTS idx_low_stock_alert_product_id
    ON procurement.low_stock_alert (product_id);

-- Finance: find journal entries by the business object they reference
CREATE INDEX IF NOT EXISTS idx_journal_entry_reference_id
    ON finance.journal_entry (reference_id);

-- Finance: find journal lines for a journal entry
CREATE INDEX IF NOT EXISTS idx_journal_line_journal_entry_id
    ON finance.journal_line (journal_entry_id);

-- Finance: find payments for an invoice
CREATE INDEX IF NOT EXISTS idx_supplier_payment_invoice_id
    ON finance.supplier_payment (invoice_id);

-- Procurement: find purchase order lines for an order
CREATE INDEX IF NOT EXISTS idx_purchase_order_line_purchase_order_id
    ON procurement.purchase_order_line (purchase_order_id);
