-- Settlement report metadata — one row per imported report
CREATE TABLE settlement_reports (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id   UUID        NOT NULL,
    period_from     TIMESTAMPTZ NOT NULL,
    period_to       TIMESTAMPTZ NOT NULL,
    line_count      INT         NOT NULL DEFAULT 0,
    imported_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Financial exceptions: discrepancies between our ledger and the provider's report
CREATE TABLE settlement_exceptions (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id               UUID        NOT NULL REFERENCES settlement_reports(id),
    connection_id           UUID        NOT NULL,
    provider_order_ref      TEXT        NOT NULL,
    exception_type          TEXT        NOT NULL,
    expected_value          TEXT,
    reported_value          TEXT,
    tolerance_applied_minor BIGINT,
    status                  TEXT        NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'RESOLVED', 'WAIVED')),
    resolution_adjustment_id UUID       REFERENCES ledger_entries(id),
    resolution_note         TEXT,
    resolved_by             UUID,
    resolved_at             TIMESTAMPTZ,
    detected_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (report_id, provider_order_ref, exception_type)
);

-- Partial unique index: only one OPEN exception per (connection, order, type)
CREATE UNIQUE INDEX settlement_exceptions_open_dedup_idx
    ON settlement_exceptions (connection_id, provider_order_ref, exception_type)
    WHERE status = 'OPEN';

CREATE INDEX settlement_exceptions_status_idx
    ON settlement_exceptions (connection_id, status, detected_at DESC);
