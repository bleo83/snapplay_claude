-- Disney webhook subscriptions — one per connection
CREATE TABLE disney_subscriptions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id   UUID        NOT NULL UNIQUE,
    webhook_url     TEXT        NOT NULL,
    webhook_secret  TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Outbound milestone delivery log: persistent retry state + audit trail
CREATE TABLE milestone_deliveries (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id     UUID        NOT NULL UNIQUE,          -- stable idempotency key across retries
    connection_id       UUID        NOT NULL,
    handoff_session_id  UUID        NOT NULL,
    provider_order_id   UUID        NOT NULL,
    milestone           TEXT        NOT NULL,
    payload             TEXT        NOT NULL,                 -- pre-serialised JSON body sent to Disney
    status              TEXT        NOT NULL DEFAULT 'PENDING', -- PENDING | DELIVERED | DEAD_LETTER
    attempt_count       INT         NOT NULL DEFAULT 0,
    next_retry_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Partial index: only un-delivered rows need to be scanned by the scheduler
CREATE INDEX milestone_deliveries_pending_idx
    ON milestone_deliveries (next_retry_at)
    WHERE status = 'PENDING';

-- Ephemeral polling tokens — Disney polls this endpoint instead of receiving webhooks
CREATE TABLE handoff_polling_tokens (
    token_hash          TEXT        PRIMARY KEY,
    handoff_session_id  UUID        NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX handoff_polling_tokens_expires_idx
    ON handoff_polling_tokens (expires_at);
