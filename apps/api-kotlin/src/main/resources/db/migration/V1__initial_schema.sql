-- Snap Play initial schema.
-- Ported from Supabase migration: Supabase-specific items removed:
--   - auth.users FK references (user_id columns kept as plain uuid)
--   - RLS policies and enable row level security statements
--   - auth.uid() function references
--   - revoke all ... from anon, authenticated statements
--   - create extension pgcrypto (gen_random_uuid() is built-in since PG 13)
--   - is_organization_member() function (enforced in application layer)

create type organization_type as enum (
  'CONTENT_PROVIDER', 'COMMERCE_PROVIDER', 'ORCHESTRATOR', 'BRAND', 'MERCHANT'
);

create type lifecycle_status as enum ('DRAFT', 'IN_REVIEW', 'PUBLISHED', 'PAUSED', 'RETIRED');

create type handoff_mode as enum ('STORE_DEEPLINK', 'DYNAMIC_STOREFRONT', 'CART_HANDOFF', 'ORDER_API');

create type data_sharing_mode as enum ('AGGREGATED', 'PSEUDONYMOUS', 'IDENTIFIED_WITH_CONSENT', 'BILLING_ONLY');

create type order_status as enum (
  'PLACED', 'CONFIRMED', 'PREPARING', 'COURIER_ASSIGNED', 'PICKED_UP',
  'NEAR_DESTINATION', 'ARRIVED_AT_DESTINATION', 'DELIVERED', 'REJECTED',
  'CANCELLED', 'PARTIALLY_REFUNDED', 'REFUNDED', 'FAILED'
);

create table organizations (
  id uuid primary key default gen_random_uuid(),
  legal_name text not null,
  display_name text not null,
  organization_type organization_type not null,
  country char(2) not null check (country ~ '^[A-Z]{2}$'),
  default_currency char(3) not null check (default_currency ~ '^[A-Z]{3}$'),
  timezone text not null,
  status text not null default 'ACTIVE' check (status in ('PENDING', 'ACTIVE', 'SUSPENDED', 'CLOSED')),
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table organization_members (
  organization_id uuid not null references organizations(id) on delete cascade,
  user_id uuid not null,
  role_key text not null check (role_key in (
    'ORGANIZATION_ADMIN', 'CONTENT_MANAGER', 'PUBLISHER', 'CATALOG_VIEWER',
    'ANALYST', 'FINANCE', 'AUDITOR'
  )),
  status text not null default 'ACTIVE' check (status in ('INVITED', 'ACTIVE', 'SUSPENDED')),
  created_at timestamptz not null default now(),
  primary key (organization_id, user_id, role_key)
);

create table data_sharing_policies (
  id uuid primary key default gen_random_uuid(),
  owner_organization_id uuid not null references organizations(id),
  name text not null,
  version integer not null default 1 check (version > 0),
  mode data_sharing_mode not null,
  allowed_fields jsonb not null default '[]'::jsonb,
  purposes jsonb not null default '[]'::jsonb,
  retention_days integer check (retention_days is null or retention_days > 0),
  consent_required boolean not null default false,
  status text not null default 'APPROVED' check (status in ('DRAFT', 'APPROVED', 'RETIRED')),
  created_at timestamptz not null default now(),
  unique (owner_organization_id, name, version)
);

create table connections (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  content_organization_id uuid not null references organizations(id),
  commerce_organization_id uuid not null references organizations(id),
  connector_key text not null,
  environment text not null check (environment in ('SANDBOX', 'PRODUCTION')),
  status text not null default 'PENDING' check (status in ('PENDING', 'ACTIVE', 'DEGRADED', 'SUSPENDED', 'CLOSED')),
  territories char(2)[] not null,
  capabilities jsonb not null,
  data_sharing_policy_id uuid not null references data_sharing_policies(id),
  catalog_last_synced_at timestamptz,
  last_event_received_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (content_organization_id <> commerce_organization_id)
);

create table channels (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references organizations(id) on delete cascade,
  channel_key text not null,
  display_name text not null,
  status text not null default 'ACTIVE' check (status in ('ACTIVE', 'INACTIVE')),
  created_at timestamptz not null default now(),
  unique (organization_id, channel_key)
);

create table content_contexts (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references organizations(id) on delete cascade,
  channel_id uuid not null references channels(id),
  external_ref text not null,
  context_type text not null check (context_type in ('CHANNEL', 'MOVIE', 'SERIES', 'EPISODE', 'LIVE_EVENT', 'COURSE', 'LESSON', 'EDITORIAL')),
  title text not null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  unique (organization_id, external_ref)
);

create table catalog_products (
  id uuid primary key default gen_random_uuid(),
  connection_id uuid not null references connections(id) on delete cascade,
  provider_product_id text not null,
  name text not null,
  description text,
  image_url text,
  brand text,
  categories text[] not null default '{}',
  age_restricted boolean not null default false,
  reference_price_minor bigint check (reference_price_minor is null or reference_price_minor >= 0),
  currency char(3),
  status text not null default 'ACTIVE' check (status in ('ACTIVE', 'INACTIVE')),
  provider_updated_at timestamptz,
  updated_at timestamptz not null default now(),
  unique (connection_id, provider_product_id)
);

create table commercial_contracts (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  publisher_organization_id uuid not null references organizations(id),
  commerce_organization_id uuid not null references organizations(id),
  territories char(2)[] not null,
  status text not null default 'DRAFT' check (status in ('DRAFT', 'ACTIVE', 'EXPIRED', 'TERMINATED')),
  created_at timestamptz not null default now()
);

create table contract_versions (
  id uuid primary key default gen_random_uuid(),
  contract_id uuid not null references commercial_contracts(id) on delete cascade,
  version integer not null check (version > 0),
  status text not null default 'DRAFT' check (status in ('DRAFT', 'IN_REVIEW', 'APPROVED')),
  currency char(3) not null,
  effective_from timestamptz not null,
  effective_to timestamptz,
  billable_event text not null check (billable_event in ('ORDER_PLACED', 'ORDER_CONFIRMED', 'DELIVERED', 'REFUND_WINDOW_ELAPSED', 'PROVIDER_SETTLED')),
  refund_window_days integer not null default 0,
  rules jsonb not null,
  data_sharing_policy_id uuid not null references data_sharing_policies(id),
  created_at timestamptz not null default now(),
  unique (contract_id, version),
  check (effective_to is null or effective_to > effective_from)
);

create table experiences (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references organizations(id) on delete cascade,
  name text not null,
  content_context_id uuid not null references content_contexts(id),
  connection_id uuid not null references connections(id),
  contract_id uuid not null references commercial_contracts(id),
  status lifecycle_status not null default 'DRAFT',
  current_version integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (organization_id, name)
);

create table experience_versions (
  id uuid primary key default gen_random_uuid(),
  experience_id uuid not null references experiences(id) on delete cascade,
  version integer not null,
  status lifecycle_status not null default 'DRAFT',
  handoff_mode handoff_mode not null,
  product_ids uuid[] not null default '{}',
  fallback_collection_id text,
  store_selection jsonb not null default '{}'::jsonb,
  eligibility jsonb not null,
  presentation jsonb not null,
  effective_from timestamptz not null,
  effective_to timestamptz,
  created_at timestamptz not null default now(),
  unique (experience_id, version),
  check (effective_to is null or effective_to > effective_from)
);

create table smart_links (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references organizations(id) on delete cascade,
  experience_id uuid not null references experiences(id),
  short_code text not null unique check (length(short_code) between 12 and 32),
  placement_key text not null,
  status text not null default 'ACTIVE' check (status in ('ACTIVE', 'PAUSED', 'EXPIRED', 'RETIRED')),
  expires_at timestamptz,
  created_at timestamptz not null default now(),
  unique (organization_id, experience_id, placement_key)
);

create table handoff_sessions (
  id uuid primary key default gen_random_uuid(),
  smart_link_id uuid not null references smart_links(id),
  experience_version_id uuid not null references experience_versions(id),
  connection_id uuid not null references connections(id),
  contract_version_id uuid not null references contract_versions(id),
  tracking_token_hash char(64) not null unique,
  status text not null default 'CREATED' check (status in ('CREATED', 'DESTINATION_RESOLVED', 'REDIRECTED', 'CHECKOUT_STARTED', 'CONVERTED', 'EXPIRED', 'FAILED')),
  publisher_user_ref text,
  data_sharing_mode data_sharing_mode not null,
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table provider_orders (
  id uuid primary key default gen_random_uuid(),
  connection_id uuid not null references connections(id),
  handoff_id uuid references handoff_sessions(id),
  provider_order_ref text not null,
  status order_status not null,
  currency char(3) not null,
  order_total_minor bigint not null check (order_total_minor >= 0),
  eligible_item_value_minor bigint check (eligible_item_value_minor is null or eligible_item_value_minor >= 0),
  publisher_user_ref text,
  placed_at timestamptz not null,
  delivered_at timestamptz,
  updated_at timestamptz not null default now(),
  unique (connection_id, provider_order_ref)
);

create table provider_order_items (
  id uuid primary key default gen_random_uuid(),
  provider_order_id uuid not null references provider_orders(id) on delete cascade,
  provider_product_id text,
  name text not null,
  category text,
  quantity integer not null check (quantity > 0),
  unit_amount_minor bigint not null check (unit_amount_minor >= 0),
  total_amount_minor bigint not null check (total_amount_minor >= 0),
  eligible_for_revenue_share boolean not null default false,
  unique (provider_order_id, provider_product_id)
);

create table partner_events (
  id uuid primary key default gen_random_uuid(),
  event_id text not null unique,
  event_type text not null,
  connection_id uuid not null references connections(id),
  provider_order_ref text,
  payload jsonb not null,
  signature_timestamp timestamptz not null,
  status text not null default 'RECEIVED' check (status in ('RECEIVED', 'PROCESSED', 'REJECTED')),
  error_detail text,
  received_at timestamptz not null default now(),
  processed_at timestamptz
);

create table order_status_history (
  id uuid primary key default gen_random_uuid(),
  provider_order_id uuid not null references provider_orders(id) on delete cascade,
  partner_event_id uuid references partner_events(id),
  from_status order_status,
  to_status order_status not null,
  occurred_at timestamptz not null,
  recorded_at timestamptz not null default now(),
  unique (provider_order_id, to_status, occurred_at)
);

create table outbox_events (
  id uuid primary key default gen_random_uuid(),
  aggregate_type text not null,
  aggregate_id uuid not null,
  event_type text not null,
  schema_version text not null,
  payload jsonb not null,
  occurred_at timestamptz not null,
  published_at timestamptz,
  attempts integer not null default 0,
  last_error text,
  created_at timestamptz not null default now()
);

create table audit_log (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid references organizations(id),
  actor_user_id uuid,
  action text not null,
  resource_type text not null,
  resource_id text not null,
  request_id text not null,
  before_redacted jsonb,
  after_redacted jsonb,
  occurred_at timestamptz not null default now()
);

create index catalog_products_connection_idx on catalog_products(connection_id, status);
create index experiences_org_status_idx on experiences(organization_id, status);
create index smart_links_code_idx on smart_links(short_code) where status = 'ACTIVE';
create index orders_connection_time_idx on provider_orders(connection_id, placed_at desc);
create index orders_handoff_idx on provider_orders(handoff_id);
create index partner_events_connection_received_idx on partner_events(connection_id, received_at desc);
create index order_history_order_time_idx on order_status_history(provider_order_id, occurred_at);
create index outbox_unpublished_idx on outbox_events(created_at) where published_at is null;
