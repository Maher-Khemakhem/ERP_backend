-- Add the address column that was accepted by the API but never persisted.
ALTER TABLE procurement.supplier
    ADD COLUMN IF NOT EXISTS address VARCHAR(500);
