-- Append-only financial ledger. The application NEVER issues UPDATE on amount fields.
CREATE TABLE ledger_entries (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_type          TEXT        NOT NULL CHECK (entry_type IN ('EARN', 'REVERSAL', 'ADJUSTMENT')),
    status              TEXT        NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'EARNED', 'REVERSED', 'SETTLED')),
    provider_order_id   UUID        NOT NULL,
    handoff_session_id  UUID,
    contract_version_id UUID        NOT NULL,
    rule_key            TEXT        NOT NULL,           -- e.g. "platform_fee", "revenue_share"
    causative_event     TEXT        NOT NULL,           -- e.g. "ORDER_DELIVERED", "ORDER_REFUNDED", "MANUAL_ADJUSTMENT"
    party_id            UUID        NOT NULL,           -- organization receiving this entry
    counterparty_id     UUID        NOT NULL,           -- organization on the other side
    base_amount_minor   BIGINT      NOT NULL,           -- GMV or reference amount the rule was applied to
    amount_minor        BIGINT      NOT NULL,           -- signed: positive = credit, negative = debit
    currency            CHAR(3)     NOT NULL,
    original_entry_id   UUID        REFERENCES ledger_entries(id), -- for reversals/adjustments: points to the entry being reversed
    reason              TEXT,                            -- required for adjustments
    actor_id            UUID,                           -- who created the entry (null for system-generated)
    metadata            JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider_order_id, rule_key, causative_event, entry_type, COALESCE(original_entry_id, '00000000-0000-0000-0000-000000000000'))
);

CREATE INDEX ledger_entries_order_idx ON ledger_entries (provider_order_id, created_at);
CREATE INDEX ledger_entries_party_idx ON ledger_entries (party_id, currency, created_at);
CREATE INDEX ledger_entries_status_idx ON ledger_entries (status) WHERE status = 'PENDING';
