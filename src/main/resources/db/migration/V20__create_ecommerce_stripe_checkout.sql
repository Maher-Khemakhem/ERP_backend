CREATE TABLE ecommerce.stripe_checkout (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES ecommerce.online_order(id),
    checkout_session_id VARCHAR(255) UNIQUE,
    checkout_url TEXT,
    tnd_amount NUMERIC(19, 3) NOT NULL,
    eur_cents BIGINT NOT NULL,
    tnd_to_eur_rate NUMERIC(18, 8) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ecommerce_stripe_checkout_status_check CHECK (status IN ('CREATING', 'PENDING', 'PAID', 'EXPIRED'))
);
