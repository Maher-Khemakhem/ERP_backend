ALTER TABLE ecommerce.online_order
    ADD COLUMN pickup_code VARCHAR(12),
    ADD COLUMN picked_up_at TIMESTAMPTZ,
    ADD COLUMN picked_up_by VARCHAR(80);

-- Orders already awaiting collection must also receive a code.
UPDATE ecommerce.online_order
SET pickup_code = UPPER(SUBSTRING(REPLACE(gen_random_uuid()::TEXT, '-', '') FROM 1 FOR 12))
WHERE status = 'READY';

ALTER TABLE ecommerce.online_order
    ADD CONSTRAINT online_order_ready_pickup_code_check
        CHECK ((status = 'READY') = (pickup_code IS NOT NULL));
