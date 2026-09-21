CREATE TABLE IF NOT EXISTS inventory.product_family (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL UNIQUE,
    image_url VARCHAR(500)
);

ALTER TABLE inventory.product
    ADD COLUMN IF NOT EXISTS family_id UUID;

INSERT INTO inventory.product_family (id, name, image_url)
SELECT gen_random_uuid(), family_name, NULL
FROM inventory.product
WHERE family_name IS NOT NULL
  AND btrim(family_name) <> ''
GROUP BY family_name
ON CONFLICT (name) DO NOTHING;

UPDATE inventory.product p
SET family_id = f.id
FROM inventory.product_family f
WHERE p.family_id IS NULL
  AND p.family_name IS NOT NULL
  AND lower(f.name) = lower(p.family_name);

ALTER TABLE inventory.product
    ADD CONSTRAINT product_family_fk
    FOREIGN KEY (family_id) REFERENCES inventory.product_family(id);

ALTER TABLE inventory.product
    DROP COLUMN family_name;