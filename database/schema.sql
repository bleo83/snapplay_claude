-- Snap Play PostgreSQL schema — MVP Disney × Rappi
-- PostgreSQL 16+
-- Monetary values are stored as integer minor units. Published versions and ledger rows are immutable.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TYPE organization_type AS ENUM (
  'CONTENT_PROVIDER', 'COMMERCE_PROVIDER', 'ORCHESTRATOR', 'BRAND', 'MERCHANT'
);
CREATE TYPE organization_status AS ENUM ('PENDING', 'ACTIVE', 'SUSPENDED', 'CLOSED');
CREATE TYPE connection_status AS ENUM ('PENDING', 'ACTIVE', 'DEGRADED', 'SUSPENDED', 'CLOSED');
CREATE TYPE environment_type AS ENUM ('SANDBOX', 'PRODUCTION');
CREATE TYPE content_context_type AS ENUM (
  'CHANNEL', 'MOVIE', 'SERIES', 'EPISODE', 'LIVE_EVENT', 'COURSE', 'LESSON', 'EDITORIAL'
);
CREATE TYPE lifecycle_status AS ENUM ('DRAFT', 'IN_REVIEW', 'PUBLISHED', 'PAUSED', 'RETIRED');
CREATE TYPE handoff_mode AS ENUM ('STORE_DEEPLINK', 'DYNAMIC_STOREFRONT', 'CART_HANDOFF', 'ORDER_API');
CREATE TYPE handoff_status AS ENUM (
  'CREATED', 'DESTINATION_RESOLVED', 'REDIRECTED', 'CHECKOUT_STARTED', 'CONVERTED', 'EXPIRED', 'FAILED'
);
CREATE TYPE order_status AS ENUM (
  'PLACED', 'CONFIRMED', 'PREPARING', 'COURIER_ASSIGNED', 'PICKED_UP',
  'NEAR_DESTINATION', 'ARRIVED_AT_DESTINATION', 'DELIVERED', 'REJECTED',
  'CANCELLED', 'PARTIALLY_REFUNDED', 'REFUNDED', 'FAILED'
);
CREATE TYPE data_sharing_mode AS ENUM ('AGGREGATED', 'PSEUDONYMOUS', 'IDENTIFIED_WITH_CONSENT', 'BILLING_ONLY');
CREATE TYPE contract_status AS ENUM ('DRAFT', 'ACTIVE', 'EXPIRED', 'TERMINATED');
CREATE TYPE contract_version_status AS ENUM ('DRAFT', 'IN_REVIEW', 'APPROVED', 'SUPERSEDED');
CREATE TYPE billable_event_type AS ENUM (
  'ORDER_PLACED', 'ORDER_CONFIRMED', 'DELIVERED', 'REFUND_WINDOW_ELAPSED', 'PROVIDER_SETTLED'
);
CREATE TYPE commercial_rule_type AS ENUM ('ORCHESTRATION_FEE', 'REVENUE_SHARE');
CREATE TYPE commercial_basis AS ENUM (
  'ORDER_TOTAL', 'ITEM_GROSS_VALUE', 'ITEM_NET_VALUE', 'ELIGIBLE_ITEM_VALUE',
  'DELIVERY_FEE', 'FIXED_PER_CONVERTED_ORDER'
);
CREATE TYPE ledger_entry_status AS ENUM ('PENDING', 'EARNED', 'REVERSED', 'SETTLED');
CREATE TYPE settlement_status AS ENUM (
  'OPEN', 'CALCULATED', 'REVIEW', 'APPROVED', 'ISSUED', 'PAID', 'CLOSED', 'DISPUTED', 'VOIDED'
);

CREATE TABLE organizations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  legal_name text NOT NULL,
  display_name text NOT NULL,
  organization_type organization_type NOT NULL,
  status organization_status NOT NULL DEFAULT 'PENDING',
  country char(2) NOT NULL CHECK (country ~ '^[A-Z]{2}$'),
  default_currency char(3) NOT NULL CHECK (default_currency ~ '^[A-Z]{3}$'),
  timezone text NOT NULL,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE organization_members (
  organization_id uuid NOT NULL REFERENCES organizations(id),
  identity_subject text NOT NULL,
  role_key text NOT NULL CHECK (role_key IN (
    'ORGANIZATION_ADMIN', 'CONTENT_MANAGER', 'PUBLISHER', 'CATALOG_VIEWER',
    'ANALYST', 'FINANCE', 'AUDITOR'
  )),
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('INVITED', 'ACTIVE', 'SUSPENDED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (organization_id, identity_subject, role_key)
);

CREATE TABLE data_sharing_policies (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_organization_id uuid NOT NULL REFERENCES organizations(id),
  name text NOT NULL,
  version integer NOT NULL CHECK (version > 0),
  mode data_sharing_mode NOT NULL,
  allowed_fields jsonb NOT NULL DEFAULT '[]'::jsonb,
  purposes jsonb NOT NULL DEFAULT '[]'::jsonb,
  retention_days integer CHECK (retention_days IS NULL OR retention_days > 0),
  consent_required boolean NOT NULL DEFAULT false,
  status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'APPROVED', 'RETIRED')),
  approved_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (owner_organization_id, name, version),
  CHECK ((status <> 'APPROVED') OR approved_at IS NOT NULL)
);

CREATE TABLE connections (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  content_organization_id uuid NOT NULL REFERENCES organizations(id),
  commerce_organization_id uuid NOT NULL REFERENCES organizations(id),
  connector_key text NOT NULL CHECK (connector_key ~ '^[a-z][a-z0-9_-]{1,49}$'),
  connector_version text NOT NULL DEFAULT '1.0.0',
  environment environment_type NOT NULL,
  status connection_status NOT NULL DEFAULT 'PENDING',
  territories char(2)[] NOT NULL,
  capabilities jsonb NOT NULL,
  data_sharing_policy_id uuid NOT NULL REFERENCES data_sharing_policies(id),
  secret_reference text,
  destination_allowlist jsonb NOT NULL DEFAULT '[]'::jsonb,
  catalog_last_synced_at timestamptz,
  last_event_received_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (content_organization_id, commerce_organization_id, connector_key, environment),
  CHECK (content_organization_id <> commerce_organization_id)
);

CREATE TABLE channels (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organizations(id),
  channel_key text NOT NULL,
  display_name text NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (organization_id, channel_key)
);

CREATE TABLE content_contexts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organizations(id),
  channel_id uuid NOT NULL REFERENCES channels(id),
  external_ref text NOT NULL,
  context_type content_context_type NOT NULL,
  title text NOT NULL,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (organization_id, external_ref)
);

CREATE TABLE catalog_syncs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  connection_id uuid NOT NULL REFERENCES connections(id),
  mode text NOT NULL CHECK (mode IN ('FULL', 'INCREMENTAL')),
  status text NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'PARTIALLY_SUCCEEDED', 'FAILED')),
  provider_cursor text,
  source_object_url text,
  source_checksum_sha256 char(64),
  records_seen bigint NOT NULL DEFAULT 0 CHECK (records_seen >= 0),
  records_rejected bigint NOT NULL DEFAULT 0 CHECK (records_rejected >= 0),
  error_summary jsonb NOT NULL DEFAULT '{}'::jsonb,
  started_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE catalog_stores (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  connection_id uuid NOT NULL REFERENCES connections(id),
  provider_store_id text NOT NULL,
  name text NOT NULL,
  country char(2) NOT NULL,
  service_area_ref text,
  status text NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
  attributes jsonb NOT NULL DEFAULT '{}'::jsonb,
  provider_updated_at timestamptz,
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (connection_id, provider_store_id)
);

CREATE TABLE catalog_products (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  connection_id uuid NOT NULL REFERENCES connections(id),
  provider_product_id text NOT NULL,
  name text NOT NULL,
  description text,
  image_url text,
  brand text,
  categories text[] NOT NULL DEFAULT '{}',
  gtin text,
  age_restricted boolean NOT NULL DEFAULT false,
  reference_price_minor bigint CHECK (reference_price_minor IS NULL OR reference_price_minor >= 0),
  currency char(3) CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
  status text NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
  attributes jsonb NOT NULL DEFAULT '{}'::jsonb,
  provider_updated_at timestamptz,
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (connection_id, provider_product_id)
);

CREATE INDEX catalog_products_search_idx
  ON catalog_products USING gin (to_tsvector('simple', coalesce(name, '') || ' ' || coalesce(description, '')));
CREATE INDEX catalog_products_categories_idx ON catalog_products USING gin (categories);

CREATE TABLE catalog_product_store_availability (
  product_id uuid NOT NULL REFERENCES catalog_products(id) ON DELETE CASCADE,
  store_id uuid NOT NULL REFERENCES catalog_stores(id) ON DELETE CASCADE,
  available boolean NOT NULL,
  price_minor bigint CHECK (price_minor IS NULL OR price_minor >= 0),
  currency char(3) CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
  stock_state text CHECK (stock_state IN ('IN_STOCK', 'LOW_STOCK', 'OUT_OF_STOCK', 'UNKNOWN')),
  observed_at timestamptz NOT NULL,
  PRIMARY KEY (product_id, store_id)
);

CREATE TABLE commercial_contracts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  publisher_organization_id uuid NOT NULL REFERENCES organizations(id),
  commerce_organization_id uuid NOT NULL REFERENCES organizations(id),
  orchestrator_organization_id uuid REFERENCES organizations(id),
  territories char(2)[] NOT NULL,
  status contract_status NOT NULL DEFAULT 'DRAFT',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (publisher_organization_id <> commerce_organization_id)
);

CREATE TABLE contract_versions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  contract_id uuid NOT NULL REFERENCES commercial_contracts(id),
  version integer NOT NULL CHECK (version > 0),
  status contract_version_status NOT NULL DEFAULT 'DRAFT',
  territory char(2) NOT NULL,
  currency char(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
  effective_from timestamptz NOT NULL,
  effective_to timestamptz,
  billable_event billable_event_type NOT NULL,
  refund_window_days integer NOT NULL DEFAULT 0 CHECK (refund_window_days BETWEEN 0 AND 180),
  settlement_frequency text NOT NULL CHECK (settlement_frequency IN ('WEEKLY', 'MONTHLY', 'QUARTERLY')),
  timezone text NOT NULL,
  data_sharing_policy_id uuid NOT NULL REFERENCES data_sharing_policies(id),
  terms_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb,
  approved_by_subject text,
  approved_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (contract_id, version, territory, currency),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK ((status <> 'APPROVED') OR (approved_at IS NOT NULL AND approved_by_subject IS NOT NULL))
);

ALTER TABLE contract_versions ADD CONSTRAINT contract_versions_no_approved_overlap
  EXCLUDE USING gist (
    contract_id WITH =,
    territory WITH =,
    currency WITH =,
    tstzrange(effective_from, coalesce(effective_to, 'infinity'::timestamptz), '[)') WITH &&
  ) WHERE (status = 'APPROVED');

CREATE TABLE commercial_rules (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  contract_version_id uuid NOT NULL REFERENCES contract_versions(id),
  rule_key text NOT NULL,
  rule_type commercial_rule_type NOT NULL,
  beneficiary_organization_id uuid REFERENCES organizations(id),
  basis commercial_basis NOT NULL,
  fixed_amount_minor bigint CHECK (fixed_amount_minor IS NULL OR fixed_amount_minor >= 0),
  rate_bps integer CHECK (rate_bps IS NULL OR rate_bps BETWEEN 0 AND 10000),
  eligible_provider_product_ids text[] NOT NULL DEFAULT '{}',
  eligible_categories text[] NOT NULL DEFAULT '{}',
  tax_treatment text NOT NULL CHECK (tax_treatment IN ('INCLUSIVE', 'EXCLUSIVE', 'NOT_APPLICABLE')),
  priority integer NOT NULL DEFAULT 100,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (contract_version_id, rule_key),
  CHECK (
    (basis = 'FIXED_PER_CONVERTED_ORDER' AND fixed_amount_minor IS NOT NULL AND rate_bps IS NULL)
    OR
    (basis <> 'FIXED_PER_CONVERTED_ORDER' AND rate_bps IS NOT NULL)
  )
);

CREATE TABLE experiences (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organizations(id),
  name text NOT NULL,
  content_context_id uuid NOT NULL REFERENCES content_contexts(id),
  connection_id uuid NOT NULL REFERENCES connections(id),
  contract_id uuid NOT NULL REFERENCES commercial_contracts(id),
  status lifecycle_status NOT NULL DEFAULT 'DRAFT',
  current_draft_version integer NOT NULL DEFAULT 1,
  current_published_version integer,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (organization_id, name)
);

CREATE TABLE experience_versions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  experience_id uuid NOT NULL REFERENCES experiences(id),
  version integer NOT NULL CHECK (version > 0),
  status lifecycle_status NOT NULL DEFAULT 'DRAFT',
  handoff_mode handoff_mode NOT NULL,
  store_selection jsonb NOT NULL,
  eligibility jsonb NOT NULL,
  presentation jsonb NOT NULL,
  fallback_collection_id text,
  effective_from timestamptz NOT NULL,
  effective_to timestamptz,
  definition_hash char(64) NOT NULL,
  change_note text,
  created_by_subject text NOT NULL,
  published_by_subject text,
  published_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (experience_id, version),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK ((status <> 'PUBLISHED') OR published_at IS NOT NULL)
);

CREATE TABLE offers (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  experience_version_id uuid NOT NULL REFERENCES experience_versions(id) ON DELETE CASCADE,
  title text NOT NULL,
  description text,
  priority integer NOT NULL DEFAULT 100,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE offer_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  offer_id uuid NOT NULL REFERENCES offers(id) ON DELETE CASCADE,
  catalog_product_id uuid NOT NULL REFERENCES catalog_products(id),
  quantity integer NOT NULL DEFAULT 1 CHECK (quantity > 0),
  required boolean NOT NULL DEFAULT false,
  priority integer NOT NULL DEFAULT 100,
  UNIQUE (offer_id, catalog_product_id)
);

CREATE TABLE smart_links (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organizations(id),
  experience_id uuid NOT NULL REFERENCES experiences(id),
  short_code text NOT NULL UNIQUE CHECK (length(short_code) BETWEEN 12 AND 32),
  placement_key text NOT NULL,
  label text,
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAUSED', 'EXPIRED', 'RETIRED')),
  expires_at timestamptz,
  created_by_subject text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (organization_id, experience_id, placement_key)
);

CREATE TABLE scan_sessions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  smart_link_id uuid NOT NULL REFERENCES smart_links(id),
  experience_version_id uuid NOT NULL REFERENCES experience_versions(id),
  occurred_at timestamptz NOT NULL DEFAULT now(),
  country char(2),
  locale text,
  device_class text,
  bot_classification text NOT NULL DEFAULT 'UNKNOWN' CHECK (bot_classification IN ('HUMAN', 'BOT', 'UNKNOWN')),
  source_ip_hash char(64),
  user_agent_hash char(64),
  request_id text NOT NULL,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX scan_sessions_link_time_idx ON scan_sessions (smart_link_id, occurred_at DESC);

CREATE TABLE consent_receipts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  data_sharing_policy_id uuid NOT NULL REFERENCES data_sharing_policies(id),
  scan_session_id uuid REFERENCES scan_sessions(id),
  publisher_user_ref text,
  consented boolean NOT NULL,
  policy_text_hash char(64) NOT NULL,
  source text NOT NULL,
  occurred_at timestamptz NOT NULL,
  expires_at timestamptz,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE handoff_sessions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  scan_session_id uuid NOT NULL REFERENCES scan_sessions(id),
  connection_id uuid NOT NULL REFERENCES connections(id),
  contract_version_id uuid NOT NULL REFERENCES contract_versions(id),
  status handoff_status NOT NULL DEFAULT 'CREATED',
  mode handoff_mode NOT NULL,
  tracking_token_hash char(64) NOT NULL UNIQUE,
  provider_session_ref text,
  destination_url_ciphertext bytea,
  publisher_user_ref text,
  data_sharing_mode data_sharing_mode NOT NULL,
  expires_at timestamptz NOT NULL,
  redirected_at timestamptz,
  converted_at timestamptz,
  failure_code text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX handoff_connection_created_idx ON handoff_sessions (connection_id, created_at DESC);

CREATE TABLE provider_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  connection_id uuid NOT NULL REFERENCES connections(id),
  provider_event_id text NOT NULL,
  event_type text NOT NULL,
  subject text NOT NULL,
  schema_version text NOT NULL,
  occurred_at timestamptz NOT NULL,
  received_at timestamptz NOT NULL DEFAULT now(),
  payload_redacted jsonb NOT NULL,
  payload_hash char(64) NOT NULL,
  signature_key_version text NOT NULL,
  processing_status text NOT NULL DEFAULT 'RECEIVED' CHECK (
    processing_status IN ('RECEIVED', 'PROCESSED', 'REJECTED', 'DEAD_LETTER')
  ),
  error_code text,
  UNIQUE (connection_id, provider_event_id)
);

CREATE TABLE provider_orders (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  connection_id uuid NOT NULL REFERENCES connections(id),
  handoff_id uuid REFERENCES handoff_sessions(id),
  provider_order_ref text NOT NULL,
  provider_order_ref_hash char(64) NOT NULL,
  status order_status NOT NULL,
  currency char(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
  order_total_minor bigint NOT NULL CHECK (order_total_minor >= 0),
  item_gross_value_minor bigint CHECK (item_gross_value_minor IS NULL OR item_gross_value_minor >= 0),
  item_net_value_minor bigint CHECK (item_net_value_minor IS NULL OR item_net_value_minor >= 0),
  eligible_item_value_minor bigint CHECK (eligible_item_value_minor IS NULL OR eligible_item_value_minor >= 0),
  delivery_fee_minor bigint CHECK (delivery_fee_minor IS NULL OR delivery_fee_minor >= 0),
  discount_total_minor bigint CHECK (discount_total_minor IS NULL OR discount_total_minor >= 0),
  contract_version_id uuid NOT NULL REFERENCES contract_versions(id),
  publisher_user_ref text,
  placed_at timestamptz NOT NULL,
  delivered_at timestamptz,
  cancelled_at timestamptz,
  updated_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (connection_id, provider_order_ref)
);
CREATE INDEX provider_orders_handoff_idx ON provider_orders (handoff_id);
CREATE INDEX provider_orders_contract_time_idx ON provider_orders (contract_version_id, placed_at);

CREATE TABLE provider_order_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  provider_order_id uuid NOT NULL REFERENCES provider_orders(id) ON DELETE CASCADE,
  provider_product_id text,
  name text NOT NULL,
  category text,
  quantity integer NOT NULL CHECK (quantity > 0),
  unit_amount_minor bigint NOT NULL CHECK (unit_amount_minor >= 0),
  total_amount_minor bigint NOT NULL CHECK (total_amount_minor >= 0),
  eligible_for_revenue_share boolean NOT NULL DEFAULT false,
  item_metadata jsonb NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE order_status_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  provider_order_id uuid NOT NULL REFERENCES provider_orders(id) ON DELETE CASCADE,
  provider_event_id uuid REFERENCES provider_events(id),
  from_status order_status,
  to_status order_status NOT NULL,
  occurred_at timestamptz NOT NULL,
  recorded_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (provider_order_id, to_status, occurred_at)
);

CREATE TABLE order_refunds (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  provider_order_id uuid NOT NULL REFERENCES provider_orders(id),
  provider_refund_ref text NOT NULL,
  amount_minor bigint NOT NULL CHECK (amount_minor >= 0),
  eligible_amount_minor bigint CHECK (eligible_amount_minor IS NULL OR eligible_amount_minor >= 0),
  currency char(3) NOT NULL,
  reason_code text,
  occurred_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (provider_order_id, provider_refund_ref)
);

CREATE TABLE ledger_entries (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  provider_order_id uuid NOT NULL REFERENCES provider_orders(id),
  contract_version_id uuid NOT NULL REFERENCES contract_versions(id),
  commercial_rule_id uuid NOT NULL REFERENCES commercial_rules(id),
  source_event_id uuid REFERENCES provider_events(id),
  reversal_of_entry_id uuid REFERENCES ledger_entries(id),
  debit_organization_id uuid NOT NULL REFERENCES organizations(id),
  credit_organization_id uuid NOT NULL REFERENCES organizations(id),
  currency char(3) NOT NULL,
  base_amount_minor bigint NOT NULL,
  amount_minor bigint NOT NULL,
  status ledger_entry_status NOT NULL,
  calculation_snapshot jsonb NOT NULL,
  earned_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (debit_organization_id <> credit_organization_id),
  CHECK (amount_minor >= 0)
);
CREATE INDEX ledger_contract_created_idx ON ledger_entries (contract_version_id, created_at);

CREATE TABLE settlements (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  contract_id uuid NOT NULL REFERENCES commercial_contracts(id),
  beneficiary_organization_id uuid NOT NULL REFERENCES organizations(id),
  period_start timestamptz NOT NULL,
  period_end timestamptz NOT NULL,
  currency char(3) NOT NULL,
  status settlement_status NOT NULL DEFAULT 'OPEN',
  billable_orders bigint NOT NULL DEFAULT 0,
  gmv_minor bigint NOT NULL DEFAULT 0,
  snapplay_fee_minor bigint NOT NULL DEFAULT 0,
  revenue_share_minor bigint NOT NULL DEFAULT 0,
  adjustments_minor bigint NOT NULL DEFAULT 0,
  net_payable_minor bigint NOT NULL DEFAULT 0,
  calculation_hash char(64),
  calculated_at timestamptz,
  approved_by_subject text,
  approved_at timestamptz,
  external_payment_ref text,
  paid_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (contract_id, beneficiary_organization_id, period_start, period_end, currency),
  CHECK (period_end > period_start),
  CHECK ((status NOT IN ('APPROVED', 'ISSUED', 'PAID', 'CLOSED')) OR approved_at IS NOT NULL)
);

CREATE TABLE settlement_lines (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  settlement_id uuid NOT NULL REFERENCES settlements(id) ON DELETE CASCADE,
  ledger_entry_id uuid REFERENCES ledger_entries(id),
  line_type text NOT NULL CHECK (line_type IN ('ORDER', 'REFUND', 'FEE', 'REVENUE_SHARE', 'ADJUSTMENT')),
  description text NOT NULL,
  amount_minor bigint NOT NULL,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (settlement_id, ledger_entry_id)
);

CREATE TABLE reconciliation_exceptions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  settlement_id uuid REFERENCES settlements(id),
  provider_order_id uuid REFERENCES provider_orders(id),
  exception_type text NOT NULL CHECK (exception_type IN (
    'MISSING_IN_SNAPPLAY', 'MISSING_IN_PROVIDER', 'STATUS_MISMATCH', 'AMOUNT_MISMATCH',
    'PRODUCT_SCOPE_MISMATCH', 'DUPLICATE_ATTRIBUTION', 'CONTRACT_NOT_FOUND',
    'LATE_REFUND', 'CURRENCY_MISMATCH'
  )),
  expected_value jsonb,
  actual_value jsonb,
  status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'WAIVED')),
  resolution_note text,
  resolved_by_subject text,
  resolved_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE webhook_subscriptions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organizations(id),
  connection_id uuid REFERENCES connections(id),
  url text NOT NULL,
  event_types text[] NOT NULL,
  data_sharing_policy_id uuid NOT NULL REFERENCES data_sharing_policies(id),
  secret_reference text NOT NULL,
  secret_version text NOT NULL,
  status text NOT NULL DEFAULT 'PENDING_VERIFICATION' CHECK (
    status IN ('PENDING_VERIFICATION', 'ACTIVE', 'PAUSED', 'DISABLED')
  ),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE webhook_deliveries (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  webhook_subscription_id uuid NOT NULL REFERENCES webhook_subscriptions(id),
  event_id text NOT NULL,
  event_type text NOT NULL,
  payload_redacted jsonb NOT NULL,
  attempt_count integer NOT NULL DEFAULT 0,
  status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DELIVERED', 'RETRYING', 'DEAD_LETTER')),
  next_attempt_at timestamptz,
  last_http_status integer,
  last_error text,
  delivered_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (webhook_subscription_id, event_id)
);

CREATE TABLE idempotency_keys (
  principal_id text NOT NULL,
  idempotency_key text NOT NULL,
  request_hash char(64) NOT NULL,
  response_status integer,
  response_body jsonb,
  resource_type text,
  resource_id uuid,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (principal_id, idempotency_key)
);

CREATE TABLE outbox_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  aggregate_type text NOT NULL,
  aggregate_id uuid NOT NULL,
  event_type text NOT NULL,
  schema_version text NOT NULL,
  payload jsonb NOT NULL,
  occurred_at timestamptz NOT NULL,
  published_at timestamptz,
  attempts integer NOT NULL DEFAULT 0,
  last_error text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX outbox_unpublished_idx ON outbox_events (created_at) WHERE published_at IS NULL;

CREATE TABLE audit_log (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid REFERENCES organizations(id),
  actor_subject text NOT NULL,
  actor_type text NOT NULL CHECK (actor_type IN ('USER', 'SERVICE_ACCOUNT', 'SYSTEM')),
  action text NOT NULL,
  resource_type text NOT NULL,
  resource_id text NOT NULL,
  reason text,
  request_id text NOT NULL,
  trace_id text,
  before_redacted jsonb,
  after_redacted jsonb,
  previous_hash char(64),
  record_hash char(64) NOT NULL,
  occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_org_time_idx ON audit_log (organization_id, occurred_at DESC);

-- Published experience versions and approved contract versions are immutable. Corrections are
-- represented by a new version or by financial reversal/adjustment rows.
CREATE FUNCTION protect_published_experience_version() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.status = 'PUBLISHED' THEN
    RAISE EXCEPTION 'published experience versions are immutable';
  END IF;
  RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE TRIGGER experience_versions_immutable
  BEFORE UPDATE OR DELETE ON experience_versions
  FOR EACH ROW EXECUTE FUNCTION protect_published_experience_version();

CREATE FUNCTION protect_approved_contract_version() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.status = 'APPROVED' THEN
    RAISE EXCEPTION 'approved contract versions are immutable';
  END IF;
  RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE TRIGGER contract_versions_immutable
  BEFORE UPDATE OR DELETE ON contract_versions
  FOR EACH ROW EXECUTE FUNCTION protect_approved_contract_version();

CREATE FUNCTION protect_published_child_row() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  parent_status lifecycle_status;
BEGIN
  IF TG_TABLE_NAME = 'offers' THEN
    SELECT status INTO parent_status
      FROM experience_versions
      WHERE id = OLD.experience_version_id;
  ELSE
    SELECT ev.status INTO parent_status
      FROM offers o
      JOIN experience_versions ev ON ev.id = o.experience_version_id
      WHERE o.id = OLD.offer_id;
  END IF;

  IF parent_status = 'PUBLISHED' THEN
    RAISE EXCEPTION 'children of published experience versions are immutable';
  END IF;
  RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE TRIGGER offers_published_parent_immutable
  BEFORE UPDATE OR DELETE ON offers
  FOR EACH ROW EXECUTE FUNCTION protect_published_child_row();

CREATE TRIGGER offer_items_published_parent_immutable
  BEFORE UPDATE OR DELETE ON offer_items
  FOR EACH ROW EXECUTE FUNCTION protect_published_child_row();

CREATE FUNCTION protect_approved_commercial_rule() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  parent_status contract_version_status;
BEGIN
  SELECT status INTO parent_status
    FROM contract_versions
    WHERE id = OLD.contract_version_id;
  IF parent_status = 'APPROVED' THEN
    RAISE EXCEPTION 'rules of approved contract versions are immutable';
  END IF;
  RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE TRIGGER commercial_rules_approved_parent_immutable
  BEFORE UPDATE OR DELETE ON commercial_rules
  FOR EACH ROW EXECUTE FUNCTION protect_approved_commercial_rule();

-- RLS is a second layer, not the only authorization mechanism. The application must set
-- `SET LOCAL app.organization_id = '<uuid>'` after validating the token.
CREATE FUNCTION current_app_organization_id() RETURNS uuid
LANGUAGE sql STABLE AS $$
  SELECT nullif(current_setting('app.organization_id', true), '')::uuid
$$;

ALTER TABLE organization_members ENABLE ROW LEVEL SECURITY;
CREATE POLICY organization_members_tenant_policy ON organization_members
  USING (organization_id = current_app_organization_id())
  WITH CHECK (organization_id = current_app_organization_id());

ALTER TABLE channels ENABLE ROW LEVEL SECURITY;
CREATE POLICY channels_tenant_policy ON channels
  USING (organization_id = current_app_organization_id())
  WITH CHECK (organization_id = current_app_organization_id());

ALTER TABLE content_contexts ENABLE ROW LEVEL SECURITY;
CREATE POLICY content_contexts_tenant_policy ON content_contexts
  USING (organization_id = current_app_organization_id())
  WITH CHECK (organization_id = current_app_organization_id());

ALTER TABLE experiences ENABLE ROW LEVEL SECURITY;
CREATE POLICY experiences_tenant_policy ON experiences
  USING (organization_id = current_app_organization_id())
  WITH CHECK (organization_id = current_app_organization_id());

ALTER TABLE smart_links ENABLE ROW LEVEL SECURITY;
CREATE POLICY smart_links_tenant_policy ON smart_links
  USING (organization_id = current_app_organization_id())
  WITH CHECK (organization_id = current_app_organization_id());

COMMIT;
