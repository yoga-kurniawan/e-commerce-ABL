-- Semua tabel dimiliki payment_db. order_id bukan foreign key lintas service.
CREATE SEQUENCE payments_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE payments (
    id BIGINT PRIMARY KEY DEFAULT nextval('payments_seq'),
    order_id BIGINT NOT NULL,
    method VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    amount NUMERIC NOT NULL,
    transaction_reference TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_payments_method CHECK (method IN ('TRANSFER', 'E_WALLET', 'COD')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    CONSTRAINT ck_payments_amount CHECK (
        amount >= 0 AND amount NOT IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)
    ),
    CONSTRAINT ck_payments_version CHECK (version >= 0)
);
ALTER SEQUENCE payments_seq OWNED BY payments.id;

-- FAILED tetap dapat banyak. Index ini merupakan pengaman tambahan bagi guard/transaksi.
CREATE UNIQUE INDEX uq_payments_order_active_or_success
    ON payments (order_id) WHERE status IN ('PENDING', 'SUCCESS');
CREATE INDEX idx_payments_order_history ON payments (order_id, id);

CREATE TABLE payment_order_guards (
    order_id BIGINT PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_payment_order_guards_version CHECK (version >= 0)
);

CREATE TABLE payment_idempotency (
    operation VARCHAR(32) COLLATE "C" NOT NULL,
    key VARCHAR(128) COLLATE "C" NOT NULL,
    order_id BIGINT NOT NULL,
    method VARCHAR(16) NOT NULL,
    fingerprint VARCHAR(64) COLLATE "C" NOT NULL,
    state VARCHAR(16) NOT NULL,
    owner_token TEXT NOT NULL,
    lease_until TIMESTAMPTZ NOT NULL,
    payment_id BIGINT,
    response_status INTEGER,
    response_content_type TEXT,
    response_body TEXT,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_payment_idempotency PRIMARY KEY (operation, key),
    CONSTRAINT fk_payment_idempotency_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT ck_payment_idempotency_operation CHECK (operation = 'CREATE_PAYMENT_V1'),
    CONSTRAINT ck_payment_idempotency_key CHECK (key ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
    CONSTRAINT ck_payment_idempotency_method CHECK (method IN ('TRANSFER', 'E_WALLET', 'COD')),
    CONSTRAINT ck_payment_idempotency_fingerprint CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_payment_idempotency_owner CHECK (btrim(owner_token) <> ''),
    CONSTRAINT ck_payment_idempotency_version CHECK (version >= 0),
    -- IS NOT NULL wajib eksplisit: CHECK yang bernilai NULL tidak menolak baris PostgreSQL.
    CONSTRAINT ck_payment_idempotency_state CHECK (
        (state = 'PROCESSING'
            AND payment_id IS NULL AND response_status IS NULL
            AND response_content_type IS NULL AND response_body IS NULL
            AND completed_at IS NULL AND expires_at IS NULL)
        OR
        (state = 'COMPLETED'
            AND payment_id IS NOT NULL AND response_status IS NOT NULL AND response_status = 201
            AND response_content_type IS NOT NULL AND response_content_type = 'application/json'
            AND response_body IS NOT NULL AND btrim(response_body) <> ''
            AND completed_at IS NOT NULL AND expires_at IS NOT NULL
            AND expires_at = completed_at + INTERVAL '24 hours')
    )
);

CREATE INDEX idx_payment_idempotency_cleanup ON payment_idempotency (expires_at)
    WHERE state = 'COMPLETED';
CREATE INDEX idx_payment_idempotency_processing_lease ON payment_idempotency (lease_until)
    WHERE state = 'PROCESSING';
CREATE INDEX idx_payment_idempotency_payment ON payment_idempotency (payment_id)
    WHERE payment_id IS NOT NULL;
