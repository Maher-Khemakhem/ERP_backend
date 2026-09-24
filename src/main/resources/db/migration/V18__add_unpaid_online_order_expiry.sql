ALTER TABLE ecommerce.online_order
    ADD COLUMN expires_at TIMESTAMPTZ,
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN expired_at TIMESTAMPTZ;

UPDATE ecommerce.online_order
SET expires_at = created_at + INTERVAL '15 minutes'
WHERE expires_at IS NULL;

ALTER TABLE ecommerce.online_order
    ALTER COLUMN expires_at SET NOT NULL;

CREATE INDEX idx_ecommerce_online_order_expiry
    ON ecommerce.online_order (expires_at)
    WHERE status = 'PENDING_PAYMENT';
