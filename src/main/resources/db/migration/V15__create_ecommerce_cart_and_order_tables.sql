CREATE SCHEMA IF NOT EXISTS ecommerce;

CREATE TABLE IF NOT EXISTS ecommerce.cart (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL UNIQUE,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS ecommerce.cart_item (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES ecommerce.cart(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    quantity_value NUMERIC(19, 3) NOT NULL,
    quantity_unit_type VARCHAR(20) NOT NULL,
    UNIQUE (cart_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_ecommerce_cart_item_cart_id
    ON ecommerce.cart_item (cart_id);

CREATE TABLE IF NOT EXISTS ecommerce.online_order (
    id UUID PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    net_amount NUMERIC(19, 3) NOT NULL,
    vat_amount NUMERIC(19, 3) NOT NULL,
    total_amount NUMERIC(19, 3) NOT NULL,
    payment_reference VARCHAR(120) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    version BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ecommerce_online_order_customer_id
    ON ecommerce.online_order (customer_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ecommerce.online_order_line (
    id UUID PRIMARY KEY,
    online_order_id UUID NOT NULL REFERENCES ecommerce.online_order(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity_value NUMERIC(19, 3) NOT NULL,
    quantity_unit_type VARCHAR(20) NOT NULL,
    unit_price NUMERIC(19, 3) NOT NULL,
    line_total NUMERIC(19, 3) NOT NULL,
    vat_amount NUMERIC(19, 3) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ecommerce_online_order_line_order_id
    ON ecommerce.online_order_line (online_order_id);
