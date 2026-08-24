CREATE TABLE idempotency_keys (
    key         VARCHAR(255) NOT NULL,
    endpoint    VARCHAR(255) NOT NULL,
    status_code INTEGER,
    response    TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (key, endpoint)
);

CREATE INDEX idx_idempotency_keys_created_at ON idempotency_keys (created_at);
