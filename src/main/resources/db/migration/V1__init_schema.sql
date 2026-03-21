CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100)        NOT NULL,
    email       VARCHAR(150)        NOT NULL UNIQUE,
    password    VARCHAR(255)        NOT NULL,
    created_at  TIMESTAMP           NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP           NOT NULL DEFAULT now()
);

CREATE TABLE wallets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID                NOT NULL UNIQUE REFERENCES users(id),
    balance     NUMERIC(19, 4)      NOT NULL DEFAULT 0.0000,
    currency    VARCHAR(3)          NOT NULL DEFAULT 'INR',
    created_at  TIMESTAMP           NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP           NOT NULL DEFAULT now()
);

CREATE TABLE transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key     VARCHAR(64)         UNIQUE,
    from_wallet_id      UUID                REFERENCES wallets(id),
    to_wallet_id        UUID                REFERENCES wallets(id),
    amount              NUMERIC(19, 4)      NOT NULL,
    type                VARCHAR(20)         NOT NULL,  -- CREDIT, DEBIT, TRANSFER
    status              VARCHAR(20)         NOT NULL DEFAULT 'SUCCESS',
    description         VARCHAR(255),
    created_at          TIMESTAMP           NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_from_wallet ON transactions(from_wallet_id);
CREATE INDEX idx_transactions_to_wallet   ON transactions(to_wallet_id);
CREATE INDEX idx_transactions_idempotency ON transactions(idempotency_key);