-- Quantity.UnitType uses JPA's default ordinal mapping: EACH = 0, WEIGHT = 1.
-- V15 initially created these columns as VARCHAR, which fails Hibernate's
-- schema validation. Convert stored enum names (or numeric legacy values)
-- without discarding existing cart/order line data.
ALTER TABLE ecommerce.cart_item
    ALTER COLUMN quantity_unit_type TYPE SMALLINT
    USING CASE quantity_unit_type
        WHEN 'EACH' THEN 0::SMALLINT
        WHEN 'WEIGHT' THEN 1::SMALLINT
        ELSE quantity_unit_type::SMALLINT
    END;

ALTER TABLE ecommerce.online_order_line
    ALTER COLUMN quantity_unit_type TYPE SMALLINT
    USING CASE quantity_unit_type
        WHEN 'EACH' THEN 0::SMALLINT
        WHEN 'WEIGHT' THEN 1::SMALLINT
        ELSE quantity_unit_type::SMALLINT
    END;
