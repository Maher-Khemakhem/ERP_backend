DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'inventory.product', 'inventory.stock_level', 'inventory.lot', 'inventory.lot_entry',
        'pricing.product_price', 'pricing.promotion',
        'pos.till', 'pos.cash_session', 'pos.pos_transaction', 'pos.transaction_line',
        'procurement.supplier', 'procurement.purchase_order', 'procurement.purchase_order_line', 'procurement.low_stock_alert',
        'finance.journal_entry', 'finance.journal_line', 'finance.ledger_entry', 'finance.supplier_invoice', 'finance.supplier_payment'
    ]
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I ON %s', replace(table_name, '.', '_') || '_tenant_policy', table_name);
        EXECUTE format('ALTER TABLE %s DISABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT IF EXISTS %I', table_name, replace(table_name, '.', '_') || '_store_fk');
        EXECUTE format('ALTER TABLE %s DROP COLUMN IF EXISTS store_id', table_name);
    END LOOP;
END $$;

DROP TABLE IF EXISTS platform.store_users;
DROP TABLE IF EXISTS platform.stores;