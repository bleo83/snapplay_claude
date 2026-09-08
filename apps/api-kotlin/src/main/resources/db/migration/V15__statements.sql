-- Monthly settlement statements
CREATE TABLE statements (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    party_id        UUID        NOT NULL,
    counterparty_id UUID        NOT NULL,
    contract_id     UUID        NOT NULL,
    period_from     TIMESTAMPTZ NOT NULL,
    period_to       TIMESTAMPTZ NOT NULL,
    currency        CHAR(3)     NOT NULL,
    total_minor     BIGINT      NOT NULL DEFAULT 0,
    line_count      INT         NOT NULL DEFAULT 0,
    status          TEXT        NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'CALCULATED', 'REVIEW', 'APPROVED', 'ISSUED', 'PAID', 'CLOSED')),
    calculated_by   UUID,
    approved_by     UUID,
    calculated_at   TIMESTAMPTZ,
    approved_at     TIMESTAMPTZ,
    issued_at       TIMESTAMPTZ,
    paid_at         TIMESTAMPTZ,
    payment_ref     TEXT,
    checksum        TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (party_id, contract_id, period_from, currency)
);

-- Statement lines: aggregated by rule_key, traceable to ledger entries
CREATE TABLE statement_lines (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    statement_id    UUID        NOT NULL REFERENCES statements(id) ON DELETE CASCADE,
    rule_key        TEXT        NOT NULL,
    description     TEXT        NOT NULL,
    entry_count     INT         NOT NULL,
    total_minor     BIGINT      NOT NULL,
    UNIQUE (statement_id, rule_key)
);
