ALTER TABLE inventory.product
    ADD COLUMN IF NOT EXISTS online_visible BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_inventory_product_online_visible
    ON inventory.product (online_visible);
