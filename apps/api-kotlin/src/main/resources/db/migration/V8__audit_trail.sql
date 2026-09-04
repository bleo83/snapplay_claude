-- Append-only audit trail. The application NEVER issues UPDATE or DELETE on this table.
CREATE TABLE audit_events (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id        TEXT        NOT NULL,
    actor_type      TEXT        NOT NULL CHECK (actor_type IN ('USER', 'SERVICE_ACCOUNT', 'SYSTEM')),
    organization_id UUID,
    role            TEXT,
    action          TEXT        NOT NULL,
    resource_type   TEXT        NOT NULL,
    resource_id     TEXT        NOT NULL,
    before_state    JSONB,
    after_state     JSONB,
    reason          TEXT,
    request_id      TEXT,
    origin          TEXT,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX audit_events_resource_idx ON audit_events (resource_type, resource_id, occurred_at DESC);
CREATE INDEX audit_events_actor_idx ON audit_events (actor_id, occurred_at DESC);
CREATE INDEX audit_events_action_idx ON audit_events (action, occurred_at DESC);
CREATE INDEX audit_events_org_idx ON audit_events (organization_id, occurred_at DESC);
