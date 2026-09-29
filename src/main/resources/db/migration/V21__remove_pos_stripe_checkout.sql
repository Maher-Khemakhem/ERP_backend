-- Preserve historical checkout rows for audit; no application code writes to this table.
ALTER TABLE pos.stripe_checkout RENAME TO stripe_checkout_legacy;
