-- V3 assumes these legacy business tables already exist. Keep this migration
-- idempotent so databases that reached V18 before it was added can apply it
-- out of order without replacing their populated tables.

CREATE TABLE IF NOT EXISTS inventory.product (
    id UUID PRIMARY KEY,
    sku VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    unit_type VARCHAR(255) NOT NULL,
    perishable BOOLEAN NOT NULL,
    reorder_point INTEGER,
    vat_rate NUMERIC(5, 2),
    CONSTRAINT product_unit_type_check CHECK (unit_type IN ('EACH', 'WEIGHT'))
);

CREATE TABLE IF NOT EXISTS inventory.stock_level (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL UNIQUE,
    version BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS inventory.lot (
    id UUID PRIMARY KEY,
    lot_number VARCHAR(255) NOT NULL,
    product_id UUID NOT NULL,
    expiry_date DATE,
    received_date DATE NOT NULL,
    supplier_reference VARCHAR(255),
    unit_cost NUMERIC(19, 3)
);

CREATE TABLE IF NOT EXISTS inventory.lot_entry (
    id UUID PRIMARY KEY,
    stock_level_id UUID REFERENCES inventory.stock_level(id),
    lot_id UUID NOT NULL UNIQUE REFERENCES inventory.lot(id),
    on_hand_value NUMERIC(38, 2),
    on_hand_unit_type SMALLINT,
    reserved_value NUMERIC(38, 2),
    reserved_unit_type SMALLINT,
    CONSTRAINT lot_entry_on_hand_unit_type_check
        CHECK (on_hand_unit_type BETWEEN 0 AND 1),
    CONSTRAINT lot_entry_reserved_unit_type_check
        CHECK (reserved_unit_type BETWEEN 0 AND 1)
);

CREATE TABLE IF NOT EXISTS pricing.product_price (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL UNIQUE,
    unit_price NUMERIC(38, 2)
);

CREATE TABLE IF NOT EXISTS pricing.promotion (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    type VARCHAR(255) NOT NULL,
    discount_percent NUMERIC(38, 2) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT promotion_type_check CHECK (type = 'PERCENTAGE_DISCOUNT')
);

CREATE TABLE IF NOT EXISTS pos.till (
    id UUID PRIMARY KEY,
    till_number VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT till_status_check CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE TABLE IF NOT EXISTS pos.cash_session (
    id UUID PRIMARY KEY,
    till_id UUID NOT NULL,
    cashier_name VARCHAR(255),
    opening_float NUMERIC(38, 2),
    expected_cash NUMERIC(38, 2),
    counted_cash NUMERIC(38, 2),
    status VARCHAR(255) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    version BIGINT NOT NULL,
    CONSTRAINT cash_session_status_check CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE TABLE IF NOT EXISTS pos.pos_transaction (
    id UUID PRIMARY KEY,
    cash_session_id UUID NOT NULL,
    status VARCHAR(255) NOT NULL,
    payment_method VARCHAR(255),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL,
    CONSTRAINT pos_transaction_status_check
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'VOIDED')),
    CONSTRAINT pos_transaction_payment_method_check
        CHECK (payment_method IN ('CASH', 'CARD'))
);

CREATE TABLE IF NOT EXISTS pos.transaction_line (
    id UUID PRIMARY KEY,
    transaction_id UUID REFERENCES pos.pos_transaction(id),
    product_id UUID NOT NULL,
    value NUMERIC(38, 2),
    unit_type SMALLINT,
    line_total NUMERIC(38, 2),
    vat_amount NUMERIC(19, 3),
    CONSTRAINT transaction_line_unit_type_check CHECK (unit_type BETWEEN 0 AND 1)
);

CREATE TABLE IF NOT EXISTS procurement.supplier (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(255),
    contact_email VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS procurement.purchase_order (
    id UUID PRIMARY KEY,
    supplier_id UUID NOT NULL,
    status VARCHAR(255) NOT NULL,
    placed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT purchase_order_status_check
        CHECK (status IN ('DRAFT', 'PLACED', 'RECEIVED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS procurement.purchase_order_line (
    id UUID PRIMARY KEY,
    purchase_order_id UUID REFERENCES procurement.purchase_order(id),
    product_id UUID NOT NULL,
    value NUMERIC(38, 2),
    unit_type SMALLINT,
    unit_cost NUMERIC(38, 2),
    CONSTRAINT purchase_order_line_unit_type_check CHECK (unit_type BETWEEN 0 AND 1)
);

CREATE TABLE IF NOT EXISTS procurement.low_stock_alert (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    raised_at TIMESTAMPTZ NOT NULL,
    acknowledged BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS finance.journal_entry (
    id UUID PRIMARY KEY,
    event_key VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL,
    reference_id UUID,
    posted_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS finance.journal_line (
    id UUID PRIMARY KEY,
    journal_entry_id UUID NOT NULL REFERENCES finance.journal_entry(id),
    account_code VARCHAR(255) NOT NULL,
    debit_amount NUMERIC(19, 3) NOT NULL,
    credit_amount NUMERIC(19, 3) NOT NULL,
    description VARCHAR(255) NOT NULL,
    CONSTRAINT journal_line_account_code_check CHECK (account_code IN (
        'CASH_ON_HAND', 'BANK', 'CARD_CLEARING', 'INVENTORY',
        'GOODS_RECEIVED_NOT_INVOICED', 'ACCOUNTS_PAYABLE', 'SALES_REVENUE',
        'OUTPUT_VAT', 'INPUT_VAT', 'COST_OF_GOODS_SOLD', 'CASH_VARIANCE'
    ))
);

CREATE TABLE IF NOT EXISTS finance.ledger_entry (
    id UUID PRIMARY KEY,
    type VARCHAR(255) NOT NULL,
    amount NUMERIC(38, 2),
    description VARCHAR(255) NOT NULL,
    reference_id UUID,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ledger_entry_type_check
        CHECK (type IN ('REVENUE', 'ACCOUNTS_PAYABLE', 'CASH_VARIANCE'))
);

CREATE TABLE IF NOT EXISTS finance.supplier_invoice (
    id UUID PRIMARY KEY,
    supplier_id UUID NOT NULL,
    purchase_order_id UUID,
    invoice_number VARCHAR(255) NOT NULL UNIQUE,
    subtotal NUMERIC(19, 3) NOT NULL,
    tax_amount NUMERIC(19, 3) NOT NULL,
    total_amount NUMERIC(19, 3) NOT NULL,
    paid_amount NUMERIC(19, 3) NOT NULL,
    status VARCHAR(255) NOT NULL,
    issue_date DATE NOT NULL,
    due_date DATE,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT supplier_invoice_status_check
        CHECK (status IN ('OPEN', 'PARTIALLY_PAID', 'PAID', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS finance.supplier_payment (
    id UUID PRIMARY KEY,
    payment_reference VARCHAR(255) NOT NULL UNIQUE,
    invoice_id UUID NOT NULL,
    amount NUMERIC(19, 3) NOT NULL,
    payment_method VARCHAR(255) NOT NULL,
    paid_date DATE NOT NULL
);

-- V11 changes both publication tables. V9 creates platform.event_publication;
-- the original public table was previously supplied by Hibernate.
CREATE TABLE IF NOT EXISTS public.event_publication (
    id UUID PRIMARY KEY,
    listener_id VARCHAR(512) NOT NULL,
    event_type VARCHAR(512) NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMPTZ NOT NULL,
    completion_date TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'INCOMPLETE',
    completion_attempts INTEGER NOT NULL DEFAULT 0,
    last_resubmission_date TIMESTAMPTZ
);
