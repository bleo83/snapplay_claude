-- Reconciliation checkpoint: one per connection, tracks the last-reconciled timestamp
CREATE TABLE reconciliation_checkpoints (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id       UUID        NOT NULL UNIQUE REFERENCES connections(id),
    last_reconciled_at  TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Reconciliation mismatches: immutable audit log; never updated, only appended or resolved
CREATE TABLE reconciliation_mismatches (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id       UUID        NOT NULL,
    provider_order_ref  TEXT        NOT NULL,
    mismatch_type       TEXT        NOT NULL,   -- MISSING_IN_SNAPPLAY | STATUS_MISMATCH | AMOUNT_MISMATCH | CURRENCY_MISMATCH
    snap_play_value     TEXT,                   -- our value (NULL if order was missing)
    rappi_value         TEXT,                   -- Rappi's value
    detected_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at         TIMESTAMPTZ
);

CREATE INDEX reconciliation_mismatches_connection_idx
    ON reconciliation_mismatches (connection_id, detected_at DESC);
