ALTER TABLE pos.pos_transaction DROP CONSTRAINT pos_transaction_payment_method_check;
ALTER TABLE pos.pos_transaction ADD CONSTRAINT pos_transaction_payment_method_check
    CHECK (payment_method IN ('CASH', 'CARD', 'STRIPE'));

CREATE TABLE pos.stripe_checkout (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL UNIQUE REFERENCES pos.pos_transaction(id),
    checkout_session_id VARCHAR(255) UNIQUE,
    checkout_url TEXT,
    tnd_amount NUMERIC(19, 3) NOT NULL,
    eur_cents BIGINT NOT NULL,
    tnd_to_eur_rate NUMERIC(18, 8) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT stripe_checkout_status_check CHECK (status IN ('CREATING', 'PENDING', 'PAID', 'FAILED'))
);
