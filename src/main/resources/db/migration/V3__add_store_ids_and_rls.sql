ALTER TABLE inventory.product ADD COLUMN IF NOT EXISTS store_id UUID;
ALTER TABLE inventory.stock_level ADD COLUMN IF NOT EXISTS store_id UUID;
ALTER TABLE inventory.lot ADD COLUMN IF NOT EXISTS store_id UUID;
ALTER TABLE inventory.lot_entry ADD COLUMN IF NOT EXISTS store_id UUID;

ALTER TABLE pricing.product_price ADD COLUMN IF NOT EXISTS store_id UUID;
ALTER TABLE pricing.promotion ADD COLUMN IF NOT EXISTS store_id UUID;

ALTER TABLE pos.till ADD COLUMN IF NOT EXISTS store_id UUID;
ALTER TABLE pos.cash_session ADD COLUMN IF NOT EXISTS store_id UUID;
ALTER TABLE pos.pos_transaction ADD COLUMN IF NOT EXISTS store_id UUID;
ALTER TABLE pos.transaction_line ADD COLUMN IF NOT EXISTS store_id UUID;

ALTER TABLE procurement.supplier ADD COLUMN IF NOT EXISTS store_id UUID;
ALTER TABLE procurement.purchase_order ADD COLUMN IF NOT EXISTS store_id UUID;
ALTER TABLE procurement.purchase_order_line ADD COLUMN IF NOT EXISTS store_id UUID;
ALTER TABLE procurement.low_stock_alert ADD COLUMN IF NOT EXISTS store_id UUID;

ALTER TABLE finance.journal_entry ADD COLUMN IF NOT EXISTS store_id UUID;
ALTER TABLE finance.journal_line ADD COLUMN IF NOT EXISTS store_id UUID;
ALTER TABLE finance.ledger_entry ADD COLUMN IF NOT EXISTS store_id UUID;
ALTER TABLE finance.supplier_invoice ADD COLUMN IF NOT EXISTS store_id UUID;
ALTER TABLE finance.supplier_payment ADD COLUMN IF NOT EXISTS store_id UUID;

UPDATE inventory.product SET store_id = '00000000-0000-0000-0000-000000000001' WHERE store_id IS NULL;
UPDATE inventory.stock_level SET store_id = '00000000-0000-0000-0000-000000000001' WHERE store_id IS NULL;
UPDATE inventory.lot SET store_id = '00000000-0000-0000-0000-000000000001' WHERE store_id IS NULL;
UPDATE inventory.lot_entry SET store_id = '00000000-0000-0000-0000-000000000001' WHERE store_id IS NULL;
UPDATE pricing.product_price SET store_id = '00000000-0000-0000-0000-000000000001' WHERE store_id IS NULL;
UPDATE pricing.promotion SET store_id = '00000000-0000-0000-0000-000000000001' WHERE store_id IS NULL;
UPDATE pos.till SET store_id = '00000000-0000-0000-0000-000000000001' WHERE store_id IS NULL;
UPDATE pos.cash_session SET store_id = '00000000-0000-0000-0000-000000000001' WHERE store_id IS NULL;
UPDATE pos.pos_transaction SET store_id = '00000000-0000-0000-0000-000000000001' WHERE store_id IS NULL;
UPDATE pos.transaction_line SET store_id = '00000000-0000-0000-0000-000000000001' WHERE store_id IS NULL;
UPDATE procurement.supplier SET store_id = '00000000-0000-0000-0000-000000000001' WHERE store_id IS NULL;
UPDATE procurement.purchase_order SET store_id = '00000000-0000-0000-0000-000000000001' WHERE store_id IS NULL;
UPDATE procurement.purchase_order_line SET store_id = '00000000-0000-0000-0000-000000000001' WHERE store_id IS NULL;
UPDATE procurement.low_stock_alert SET store_id = '00000000-0000-0000-0000-000000000001' WHERE store_id IS NULL;
UPDATE finance.journal_entry SET store_id = '00000000-0000-0000-0000-000000000001' WHERE store_id IS NULL;
UPDATE finance.journal_line SET store_id = '00000000-0000-0000-0000-000000000001' WHERE store_id IS NULL;
UPDATE finance.ledger_entry SET store_id = '00000000-0000-0000-0000-000000000001' WHERE store_id IS NULL;
UPDATE finance.supplier_invoice SET store_id = '00000000-0000-0000-0000-000000000001' WHERE store_id IS NULL;
UPDATE finance.supplier_payment SET store_id = '00000000-0000-0000-0000-000000000001' WHERE store_id IS NULL;

ALTER TABLE inventory.product ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE inventory.stock_level ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE inventory.lot ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE inventory.lot_entry ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE pricing.product_price ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE pricing.promotion ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE pos.till ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE pos.cash_session ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE pos.pos_transaction ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE pos.transaction_line ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE procurement.supplier ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE procurement.purchase_order ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE procurement.purchase_order_line ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE procurement.low_stock_alert ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE finance.journal_entry ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE finance.journal_line ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE finance.ledger_entry ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE finance.supplier_invoice ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE finance.supplier_payment ALTER COLUMN store_id SET NOT NULL;

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
        EXECUTE format(
            'ALTER TABLE %s ADD CONSTRAINT %I FOREIGN KEY (store_id) REFERENCES platform.stores(id)',
            table_name,
            replace(table_name, '.', '_') || '_store_fk'
        );
    END LOOP;
END $$;

DO $$
DECLARE
    table_name TEXT;
    policy_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'inventory.product', 'inventory.stock_level', 'inventory.lot', 'inventory.lot_entry',
        'pricing.product_price', 'pricing.promotion',
        'pos.till', 'pos.cash_session', 'pos.pos_transaction', 'pos.transaction_line',
        'procurement.supplier', 'procurement.purchase_order', 'procurement.purchase_order_line', 'procurement.low_stock_alert',
        'finance.journal_entry', 'finance.journal_line', 'finance.ledger_entry', 'finance.supplier_invoice', 'finance.supplier_payment'
    ]
    LOOP
        policy_name := replace(table_name, '.', '_') || '_tenant_policy';
        EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('DROP POLICY IF EXISTS %I ON %s', policy_name, table_name);
        EXECUTE format(
            'CREATE POLICY %I ON %s USING (store_id = NULLIF(current_setting(''app.current_store_id'', true), '''')::uuid) WITH CHECK (store_id = NULLIF(current_setting(''app.current_store_id'', true), '''')::uuid)',
            policy_name,
            table_name
        );
    END LOOP;
END $$;
