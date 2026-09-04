-- Normalized stores pulled from Rappi's catalog
CREATE TABLE provider_stores (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id       UUID        NOT NULL REFERENCES connections(id),
    provider_store_id   TEXT        NOT NULL,
    name                TEXT        NOT NULL,
    country             CHAR(2)     NOT NULL CHECK (country ~ '^[A-Z]{2}$'),
    status              TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'STALE')),
    metadata            JSONB       NOT NULL DEFAULT '{}'::jsonb,
    last_synced_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (connection_id, provider_store_id)
);

CREATE INDEX provider_stores_connection_status_idx
    ON provider_stores (connection_id, status);

-- Normalized categories pulled from Rappi's catalog
CREATE TABLE provider_categories (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id           UUID        NOT NULL REFERENCES connections(id),
    provider_category_id    TEXT        NOT NULL,
    name                    TEXT        NOT NULL,
    status                  TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'STALE')),
    last_synced_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (connection_id, provider_category_id)
);

CREATE INDEX provider_categories_connection_status_idx
    ON provider_categories (connection_id, status);

-- M:N relationship — a category is valid for selection only within its related store(s)
CREATE TABLE store_categories (
    store_id        UUID NOT NULL REFERENCES provider_stores(id) ON DELETE CASCADE,
    category_id     UUID NOT NULL REFERENCES provider_categories(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (store_id, category_id)
);
