-- Service accounts for OAuth 2.0 Client Credentials (Disney/Rappi machine-to-machine)
CREATE TABLE service_accounts (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT        NOT NULL,
    organization_id UUID        NOT NULL REFERENCES organizations(id),
    connection_id   UUID        REFERENCES connections(id), -- NULL = org-wide; set = scoped to one connection
    client_id       TEXT        NOT NULL UNIQUE,
    scopes          TEXT[]      NOT NULL DEFAULT '{}',
    status          TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX service_accounts_client_idx ON service_accounts (client_id) WHERE status = 'ACTIVE';
