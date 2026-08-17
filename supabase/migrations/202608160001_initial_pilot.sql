-- Snap Play pilot schema for Supabase.
-- The complete target model remains documented in database/schema.sql.

create extension if not exists pgcrypto with schema extensions;

create type public.organization_type as enum (
  'CONTENT_PROVIDER', 'COMMERCE_PROVIDER', 'ORCHESTRATOR', 'BRAND', 'MERCHANT'
);
create type public.lifecycle_status as enum ('DRAFT', 'IN_REVIEW', 'PUBLISHED', 'PAUSED', 'RETIRED');
create type public.handoff_mode as enum ('STORE_DEEPLINK', 'DYNAMIC_STOREFRONT', 'CART_HANDOFF', 'ORDER_API');
create type public.data_sharing_mode as enum ('AGGREGATED', 'PSEUDONYMOUS', 'IDENTIFIED_WITH_CONSENT', 'BILLING_ONLY');
create type public.order_status as enum (
  'PLACED', 'CONFIRMED', 'PREPARING', 'COURIER_ASSIGNED', 'PICKED_UP',
  'NEAR_DESTINATION', 'ARRIVED_AT_DESTINATION', 'DELIVERED', 'REJECTED',
  'CANCELLED', 'PARTIALLY_REFUNDED', 'REFUNDED', 'FAILED'
);

create table public.organizations (
  id uuid primary key default gen_random_uuid(),
  legal_name text not null,
  display_name text not null,
  organization_type public.organization_type not null,
  country char(2) not null check (country ~ '^[A-Z]{2}$'),
  default_currency char(3) not null check (default_currency ~ '^[A-Z]{3}$'),
  timezone text not null,
  status text not null default 'ACTIVE' check (status in ('PENDING', 'ACTIVE', 'SUSPENDED', 'CLOSED')),
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.organization_members (
  organization_id uuid not null references public.organizations(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role_key text not null check (role_key in (
    'ORGANIZATION_ADMIN', 'CONTENT_MANAGER', 'PUBLISHER', 'CATALOG_VIEWER',
    'ANALYST', 'FINANCE', 'AUDITOR'
  )),
  status text not null default 'ACTIVE' check (status in ('INVITED', 'ACTIVE', 'SUSPENDED')),
  created_at timestamptz not null default now(),
  primary key (organization_id, user_id, role_key)
);

create table public.data_sharing_policies (
  id uuid primary key default gen_random_uuid(),
  owner_organization_id uuid not null references public.organizations(id),
  name text not null,
  version integer not null default 1 check (version > 0),
  mode public.data_sharing_mode not null,
  allowed_fields jsonb not null default '[]'::jsonb,
  purposes jsonb not null default '[]'::jsonb,
  retention_days integer check (retention_days is null or retention_days > 0),
  consent_required boolean not null default false,
  status text not null default 'APPROVED' check (status in ('DRAFT', 'APPROVED', 'RETIRED')),
  created_at timestamptz not null default now(),
  unique (owner_organization_id, name, version)
);

create table public.connections (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  content_organization_id uuid not null references public.organizations(id),
  commerce_organization_id uuid not null references public.organizations(id),
  connector_key text not null,
  environment text not null check (environment in ('SANDBOX', 'PRODUCTION')),
  status text not null default 'PENDING' check (status in ('PENDING', 'ACTIVE', 'DEGRADED', 'SUSPENDED', 'CLOSED')),
  territories char(2)[] not null,
  capabilities jsonb not null,
  data_sharing_policy_id uuid not null references public.data_sharing_policies(id),
  catalog_last_synced_at timestamptz,
  last_event_received_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (content_organization_id <> commerce_organization_id)
);

create table public.channels (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete cascade,
  channel_key text not null,
  display_name text not null,
  status text not null default 'ACTIVE' check (status in ('ACTIVE', 'INACTIVE')),
  created_at timestamptz not null default now(),
  unique (organization_id, channel_key)
);

create table public.content_contexts (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete cascade,
  channel_id uuid not null references public.channels(id),
  external_ref text not null,
  context_type text not null check (context_type in ('CHANNEL', 'MOVIE', 'SERIES', 'EPISODE', 'LIVE_EVENT', 'COURSE', 'LESSON', 'EDITORIAL')),
  title text not null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  unique (organization_id, external_ref)
);

create table public.catalog_products (
  id uuid primary key default gen_random_uuid(),
  connection_id uuid not null references public.connections(id) on delete cascade,
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

create table public.commercial_contracts (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  publisher_organization_id uuid not null references public.organizations(id),
  commerce_organization_id uuid not null references public.organizations(id),
  territories char(2)[] not null,
  status text not null default 'DRAFT' check (status in ('DRAFT', 'ACTIVE', 'EXPIRED', 'TERMINATED')),
  created_at timestamptz not null default now()
);

create table public.contract_versions (
  id uuid primary key default gen_random_uuid(),
  contract_id uuid not null references public.commercial_contracts(id) on delete cascade,
  version integer not null check (version > 0),
  status text not null default 'DRAFT' check (status in ('DRAFT', 'IN_REVIEW', 'APPROVED')),
  currency char(3) not null,
  effective_from timestamptz not null,
  effective_to timestamptz,
  billable_event text not null check (billable_event in ('ORDER_PLACED', 'ORDER_CONFIRMED', 'DELIVERED', 'REFUND_WINDOW_ELAPSED', 'PROVIDER_SETTLED')),
  refund_window_days integer not null default 0,
  rules jsonb not null,
  data_sharing_policy_id uuid not null references public.data_sharing_policies(id),
  created_at timestamptz not null default now(),
  unique (contract_id, version),
  check (effective_to is null or effective_to > effective_from)
);

create table public.experiences (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete cascade,
  name text not null,
  content_context_id uuid not null references public.content_contexts(id),
  connection_id uuid not null references public.connections(id),
  contract_id uuid not null references public.commercial_contracts(id),
  status public.lifecycle_status not null default 'DRAFT',
  current_version integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (organization_id, name)
);

create table public.experience_versions (
  id uuid primary key default gen_random_uuid(),
  experience_id uuid not null references public.experiences(id) on delete cascade,
  version integer not null,
  status public.lifecycle_status not null default 'DRAFT',
  handoff_mode public.handoff_mode not null,
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

create table public.smart_links (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete cascade,
  experience_id uuid not null references public.experiences(id),
  short_code text not null unique check (length(short_code) between 12 and 32),
  placement_key text not null,
  status text not null default 'ACTIVE' check (status in ('ACTIVE', 'PAUSED', 'EXPIRED', 'RETIRED')),
  expires_at timestamptz,
  created_at timestamptz not null default now(),
  unique (organization_id, experience_id, placement_key)
);

create table public.handoff_sessions (
  id uuid primary key default gen_random_uuid(),
  smart_link_id uuid not null references public.smart_links(id),
  experience_version_id uuid not null references public.experience_versions(id),
  connection_id uuid not null references public.connections(id),
  contract_version_id uuid not null references public.contract_versions(id),
  tracking_token_hash char(64) not null unique,
  status text not null default 'CREATED' check (status in ('CREATED', 'DESTINATION_RESOLVED', 'REDIRECTED', 'CHECKOUT_STARTED', 'CONVERTED', 'EXPIRED', 'FAILED')),
  publisher_user_ref text,
  data_sharing_mode public.data_sharing_mode not null,
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.provider_orders (
  id uuid primary key default gen_random_uuid(),
  connection_id uuid not null references public.connections(id),
  handoff_id uuid references public.handoff_sessions(id),
  provider_order_ref text not null,
  status public.order_status not null,
  currency char(3) not null,
  order_total_minor bigint not null check (order_total_minor >= 0),
  eligible_item_value_minor bigint check (eligible_item_value_minor is null or eligible_item_value_minor >= 0),
  publisher_user_ref text,
  placed_at timestamptz not null,
  delivered_at timestamptz,
  updated_at timestamptz not null default now(),
  unique (connection_id, provider_order_ref)
);

create table public.provider_order_items (
  id uuid primary key default gen_random_uuid(),
  provider_order_id uuid not null references public.provider_orders(id) on delete cascade,
  provider_product_id text,
  name text not null,
  category text,
  quantity integer not null check (quantity > 0),
  unit_amount_minor bigint not null check (unit_amount_minor >= 0),
  total_amount_minor bigint not null check (total_amount_minor >= 0),
  eligible_for_revenue_share boolean not null default false,
  unique (provider_order_id, provider_product_id)
);

create table public.partner_events (
  id uuid primary key default gen_random_uuid(),
  event_id text not null unique,
  event_type text not null,
  connection_id uuid not null references public.connections(id),
  provider_order_ref text,
  payload jsonb not null,
  signature_timestamp timestamptz not null,
  status text not null default 'RECEIVED' check (status in ('RECEIVED', 'PROCESSED', 'REJECTED')),
  error_detail text,
  received_at timestamptz not null default now(),
  processed_at timestamptz
);

create table public.order_status_history (
  id uuid primary key default gen_random_uuid(),
  provider_order_id uuid not null references public.provider_orders(id) on delete cascade,
  partner_event_id uuid references public.partner_events(id),
  from_status public.order_status,
  to_status public.order_status not null,
  occurred_at timestamptz not null,
  recorded_at timestamptz not null default now(),
  unique (provider_order_id, to_status, occurred_at)
);

create table public.outbox_events (
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

create table public.audit_log (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid references public.organizations(id),
  actor_user_id uuid references auth.users(id),
  action text not null,
  resource_type text not null,
  resource_id text not null,
  request_id text not null,
  before_redacted jsonb,
  after_redacted jsonb,
  occurred_at timestamptz not null default now()
);

create index catalog_products_connection_idx on public.catalog_products(connection_id, status);
create index experiences_org_status_idx on public.experiences(organization_id, status);
create index smart_links_code_idx on public.smart_links(short_code) where status = 'ACTIVE';
create index orders_connection_time_idx on public.provider_orders(connection_id, placed_at desc);
create index orders_handoff_idx on public.provider_orders(handoff_id);
create index partner_events_connection_received_idx on public.partner_events(connection_id, received_at desc);
create index order_history_order_time_idx on public.order_status_history(provider_order_id, occurred_at);
create index outbox_unpublished_idx on public.outbox_events(created_at) where published_at is null;

create or replace function public.is_organization_member(target_organization_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.organization_members member
    where member.organization_id = target_organization_id
      and member.user_id = auth.uid()
      and member.status = 'ACTIVE'
  );
$$;

revoke all on function public.is_organization_member(uuid) from public;
grant execute on function public.is_organization_member(uuid) to authenticated;

alter table public.organizations enable row level security;
alter table public.organization_members enable row level security;
alter table public.data_sharing_policies enable row level security;
alter table public.channels enable row level security;
alter table public.content_contexts enable row level security;
alter table public.experiences enable row level security;
alter table public.smart_links enable row level security;

create policy organizations_member_read on public.organizations
  for select to authenticated
  using (public.is_organization_member(id));

create policy organization_members_self_read on public.organization_members
  for select to authenticated
  using (user_id = auth.uid());

create policy policies_member_read on public.data_sharing_policies
  for select to authenticated
  using (public.is_organization_member(owner_organization_id));

create policy channels_member_all on public.channels
  for all to authenticated
  using (public.is_organization_member(organization_id))
  with check (public.is_organization_member(organization_id));

create policy contexts_member_all on public.content_contexts
  for all to authenticated
  using (public.is_organization_member(organization_id))
  with check (public.is_organization_member(organization_id));

create policy experiences_member_all on public.experiences
  for all to authenticated
  using (public.is_organization_member(organization_id))
  with check (public.is_organization_member(organization_id));

create policy smart_links_member_all on public.smart_links
  for all to authenticated
  using (public.is_organization_member(organization_id))
  with check (public.is_organization_member(organization_id));

-- Tables containing cross-party or financial data are server-only in the pilot.
-- The API uses the service role after enforcing tenant, scope and data policy.
revoke all on public.connections from anon, authenticated;
revoke all on public.catalog_products from anon, authenticated;
revoke all on public.commercial_contracts from anon, authenticated;
revoke all on public.contract_versions from anon, authenticated;
revoke all on public.experience_versions from anon, authenticated;
revoke all on public.handoff_sessions from anon, authenticated;
revoke all on public.provider_orders from anon, authenticated;
revoke all on public.provider_order_items from anon, authenticated;
revoke all on public.partner_events from anon, authenticated;
revoke all on public.order_status_history from anon, authenticated;
revoke all on public.outbox_events from anon, authenticated;
revoke all on public.audit_log from anon, authenticated;
