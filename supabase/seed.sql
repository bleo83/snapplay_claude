-- Deterministic development-only data. No real users, orders or credentials.

insert into public.organizations (id, legal_name, display_name, organization_type, country, default_currency, timezone)
values
  ('50d2d7eb-c8fd-42df-bbb0-0ea0e2271090', 'The Walt Disney Company Argentina S.A.', 'Disney', 'CONTENT_PROVIDER', 'AR', 'ARS', 'America/Argentina/Buenos_Aires'),
  ('34e3015a-5644-401f-87ef-0155273a2c34', 'Rappi Argentina S.A.S.', 'Rappi', 'COMMERCE_PROVIDER', 'AR', 'ARS', 'America/Argentina/Buenos_Aires'),
  ('2c974553-0a69-42f5-8c9d-d3f62da34ef7', 'Snap Play Demo S.A.', 'Snap Play', 'ORCHESTRATOR', 'AR', 'ARS', 'America/Argentina/Buenos_Aires')
on conflict do nothing;

insert into public.data_sharing_policies (
  id, owner_organization_id, name, version, mode, allowed_fields, purposes, retention_days, consent_required
) values (
  '6995cd89-34ec-4cd6-84d0-a9b662292b9d',
  '50d2d7eb-c8fd-42df-bbb0-0ea0e2271090',
  'Disney Rappi pseudonymous store-only',
  1,
  'PSEUDONYMOUS',
  '["provider_order_ref","publisher_user_ref","status","currency","order_total_minor","eligible_item_value_minor","authorized_items"]',
  '["ATTRIBUTION","ANALYTICS","BILLING","RECONCILIATION"]',
  730,
  false
) on conflict do nothing;

insert into public.connections (
  id, name, content_organization_id, commerce_organization_id, connector_key, environment,
  status, territories, capabilities, data_sharing_policy_id, catalog_last_synced_at, last_event_received_at
) values (
  '24a9d6a6-f405-430e-8ba1-2cad9f3a006e',
  'Disney Argentina × Rappi Turbo',
  '50d2d7eb-c8fd-42df-bbb0-0ea0e2271090',
  '34e3015a-5644-401f-87ef-0155273a2c34',
  'rappi',
  'SANDBOX',
  'ACTIVE',
  array['AR']::char(2)[],
  '{"catalog":{"mode":"PULL_CURSOR","supports_inventory":true},"handoff":{"modes":["STORE_DEEPLINK","DYNAMIC_STOREFRONT"],"supports_product_filter":true,"supports_tracking_token":true},"orders":{"supports_webhooks":true,"supports_reconciliation_pull":true}}',
  '6995cd89-34ec-4cd6-84d0-a9b662292b9d',
  now() - interval '3 minutes',
  now() - interval '45 seconds'
) on conflict do nothing;

insert into public.channels (id, organization_id, channel_key, display_name)
values
  ('a7804184-bdb0-43ba-a548-a9eb9de3db79', '50d2d7eb-c8fd-42df-bbb0-0ea0e2271090', 'disney-plus', 'Disney+'),
  ('d8185297-1cf9-479c-8141-96eb8c86f71c', '50d2d7eb-c8fd-42df-bbb0-0ea0e2271090', 'espn', 'ESPN'),
  ('46cb386e-7639-48d1-9b33-4368bd9e18a2', '50d2d7eb-c8fd-42df-bbb0-0ea0e2271090', 'hulu', 'Hulu')
on conflict do nothing;

insert into public.content_contexts (id, organization_id, channel_id, external_ref, context_type, title, metadata)
values
  ('ec279963-2d99-46de-8697-d003823402dd', '50d2d7eb-c8fd-42df-bbb0-0ea0e2271090', 'a7804184-bdb0-43ba-a548-a9eb9de3db79', 'disney:movie:toy-story', 'MOVIE', 'Toy Story', '{"franchise":"Toy Story"}'),
  ('c2485171-648b-4f1d-8d2a-00d969e182f0', '50d2d7eb-c8fd-42df-bbb0-0ea0e2271090', 'a7804184-bdb0-43ba-a548-a9eb9de3db79', 'disney:movie:moana', 'MOVIE', 'Moana', '{"franchise":"Moana"}'),
  ('5052508a-009c-40bc-830c-03af7c275acb', '50d2d7eb-c8fd-42df-bbb0-0ea0e2271090', 'd8185297-1cf9-479c-8141-96eb8c86f71c', 'espn:channel:live', 'CHANNEL', 'ESPN Live', '{}'),
  ('2374cb9a-6449-477f-ad79-09211320a78d', '50d2d7eb-c8fd-42df-bbb0-0ea0e2271090', '46cb386e-7639-48d1-9b33-4368bd9e18a2', 'hulu:channel:classic', 'CHANNEL', 'Hulu', '{}')
on conflict do nothing;

insert into public.catalog_products (
  id, connection_id, provider_product_id, name, description, image_url, brand,
  categories, age_restricted, reference_price_minor, currency, provider_updated_at
) values
  ('71f4fceb-a8be-45c9-bd66-61c9e60fd26f', '24a9d6a6-f405-430e-8ba1-2cad9f3a006e', 'sku_toy_story_cup', 'Vaso coleccionable Toy Story', 'Vaso temático de 500 ml, edición Movie Night.', 'https://placehold.co/640x480/1f6fff/ffffff?text=Toy+Story+Cup', 'Disney', array['merchandising','movie-night'], false, 1499000, 'ARS', now()),
  ('6d32d586-e5ce-41f4-b9c4-7c891ad26f06', '24a9d6a6-f405-430e-8ba1-2cad9f3a006e', 'sku_popcorn_01', 'Pochoclos clásicos', 'Pochoclos listos para una noche de película.', 'https://placehold.co/640x480/f5c451/1d2433?text=Pochoclos', 'Turbo', array['snacks','movie-night'], false, 659000, 'ARS', now()),
  ('275f24dd-31c9-41d5-8580-ad83bfa89d61', '24a9d6a6-f405-430e-8ba1-2cad9f3a006e', 'sku_moana_tumbler', 'Vaso Moana', 'Vaso reutilizable inspirado en Moana.', 'https://placehold.co/640x480/10b7b4/ffffff?text=Moana+Tumbler', 'Disney', array['merchandising','family'], false, 1599000, 'ARS', now()),
  ('13591f43-f74b-4bf8-9e7d-766db1bb4b0d', '24a9d6a6-f405-430e-8ba1-2cad9f3a006e', 'sku_cola_15l', 'Gaseosa cola 1,5 L', 'Bebida para compartir.', 'https://placehold.co/640x480/e94235/ffffff?text=Gaseosa', 'Partner brand', array['bebidas'], false, 399000, 'ARS', now()),
  ('ba60645a-7e51-49e0-b0a7-39c956570e8b', '24a9d6a6-f405-430e-8ba1-2cad9f3a006e', 'sku_beer_473', 'Cerveza 473 ml', 'Venta sujeta a edad y jurisdicción.', 'https://placehold.co/640x480/d88b22/ffffff?text=Cerveza', 'Partner brand', array['bebidas','adultos'], true, 279000, 'ARS', now()),
  ('e91c3430-720f-4310-ad83-0bda12fd741c', '24a9d6a6-f405-430e-8ba1-2cad9f3a006e', 'sku_fries_large', 'Papas grandes', 'Papas crocantes para acompañar el partido.', 'https://placehold.co/640x480/f4b83f/1d2433?text=Papas', 'Turbo', array['comida','sports-night'], false, 499000, 'ARS', now())
on conflict do nothing;

insert into public.commercial_contracts (id, name, publisher_organization_id, commerce_organization_id, territories, status)
values ('0a2cf33c-9677-4468-9eef-7b7111494880', 'Disney × Rappi Argentina', '50d2d7eb-c8fd-42df-bbb0-0ea0e2271090', '34e3015a-5644-401f-87ef-0155273a2c34', array['AR']::char(2)[], 'ACTIVE')
on conflict do nothing;

insert into public.contract_versions (
  id, contract_id, version, status, currency, effective_from, billable_event,
  refund_window_days, rules, data_sharing_policy_id
) values (
  '14351c66-f56c-441c-9b2f-9e9c28dcbf0d',
  '0a2cf33c-9677-4468-9eef-7b7111494880',
  3,
  'APPROVED',
  'ARS',
  '2026-08-01T03:00:00Z',
  'DELIVERED',
  7,
  '[{"type":"ORCHESTRATION_FEE","basis":"FIXED_PER_CONVERTED_ORDER","amount_minor":25000},{"type":"REVENUE_SHARE","basis":"ELIGIBLE_ITEM_VALUE","rate_bps":800,"eligible_categories":["merchandising"]}]',
  '6995cd89-34ec-4cd6-84d0-a9b662292b9d'
) on conflict do nothing;

insert into public.experiences (id, organization_id, name, content_context_id, connection_id, contract_id, status, current_version)
values
  ('184a63fe-4420-46de-9894-b16110364263', '50d2d7eb-c8fd-42df-bbb0-0ea0e2271090', 'Toy Story Movie Night', 'ec279963-2d99-46de-8697-d003823402dd', '24a9d6a6-f405-430e-8ba1-2cad9f3a006e', '0a2cf33c-9677-4468-9eef-7b7111494880', 'PUBLISHED', 3),
  ('7351e94e-a88b-46bd-8f3c-a6df37c13483', '50d2d7eb-c8fd-42df-bbb0-0ea0e2271090', 'Moana Family Night', 'c2485171-648b-4f1d-8d2a-00d969e182f0', '24a9d6a6-f405-430e-8ba1-2cad9f3a006e', '0a2cf33c-9677-4468-9eef-7b7111494880', 'PUBLISHED', 2),
  ('1d79abf3-33e1-427c-aeba-c27f607f6f71', '50d2d7eb-c8fd-42df-bbb0-0ea0e2271090', 'ESPN Match Night', '5052508a-009c-40bc-830c-03af7c275acb', '24a9d6a6-f405-430e-8ba1-2cad9f3a006e', '0a2cf33c-9677-4468-9eef-7b7111494880', 'IN_REVIEW', 1),
  ('53d116c9-e676-4c49-9a2b-371d0b4d5c1a', '50d2d7eb-c8fd-42df-bbb0-0ea0e2271090', 'Hulu Classics', '2374cb9a-6449-477f-ad79-09211320a78d', '24a9d6a6-f405-430e-8ba1-2cad9f3a006e', '0a2cf33c-9677-4468-9eef-7b7111494880', 'DRAFT', 1)
on conflict do nothing;

insert into public.experience_versions (
  id, experience_id, version, status, handoff_mode, product_ids, fallback_collection_id,
  store_selection, eligibility, presentation, effective_from
) values
  ('92e4d416-3a8f-4530-87d0-4c46a9c6df3b', '184a63fe-4420-46de-9894-b16110364263', 3, 'PUBLISHED', 'DYNAMIC_STOREFRONT', array['71f4fceb-a8be-45c9-bd66-61c9e60fd26f'::uuid,'6d32d586-e5ce-41f4-b9c4-7c891ad26f06'::uuid,'13591f43-f74b-4bf8-9e7d-766db1bb4b0d'::uuid], 'movie_night', '{"strategy":"PROVIDER_RESOLVES_LOCATION","fallback_store_id":"disney_turbo_ar"}', '{"countries":["AR"]}', '{"locale":"es-AR","headline":"Completá tu noche de película"}', '2026-08-01T03:00:00Z'),
  ('a852cc19-218c-403a-8a0e-3b5ea4a071a1', '7351e94e-a88b-46bd-8f3c-a6df37c13483', 2, 'PUBLISHED', 'DYNAMIC_STOREFRONT', array['275f24dd-31c9-41d5-8580-ad83bfa89d61'::uuid,'6d32d586-e5ce-41f4-b9c4-7c891ad26f06'::uuid], 'family_night', '{"strategy":"PROVIDER_RESOLVES_LOCATION","fallback_store_id":"disney_turbo_ar"}', '{"countries":["AR"]}', '{"locale":"es-AR","headline":"Una aventura para compartir"}', '2026-08-10T03:00:00Z'),
  ('25d8f018-0ddf-45f6-b5f1-5bb5ab011f12', '1d79abf3-33e1-427c-aeba-c27f607f6f71', 1, 'IN_REVIEW', 'STORE_DEEPLINK', array['ba60645a-7e51-49e0-b0a7-39c956570e8b'::uuid,'e91c3430-720f-4310-ad83-0bda12fd741c'::uuid], 'sports_night', '{"strategy":"FIXED_STORE","store_id":"espn_turbo_ar"}', '{"countries":["AR"],"age_gate":true}', '{"locale":"es-AR","headline":"El partido se disfruta más"}', '2026-09-01T03:00:00Z')
on conflict do nothing;

insert into public.smart_links (id, organization_id, experience_id, short_code, placement_key)
values
  ('9ab6732c-093d-4fee-8efa-b5d2b6ec4d1f', '50d2d7eb-c8fd-42df-bbb0-0ea0e2271090', '184a63fe-4420-46de-9894-b16110364263', '7E1vM2kP9xQ4', 'disney-plus.toy-story.endcard'),
  ('f9aeff9c-1016-4d60-8dbe-6464cc1266cf', '50d2d7eb-c8fd-42df-bbb0-0ea0e2271090', '7351e94e-a88b-46bd-8f3c-a6df37c13483', '4G8zN7bK2mL6', 'disney-plus.moana.pause')
on conflict do nothing;

insert into public.handoff_sessions (
  id, smart_link_id, experience_version_id, connection_id, contract_version_id,
  tracking_token_hash, status, publisher_user_ref, data_sharing_mode, expires_at
) values
  ('6b7caedf-7a1d-4b64-8907-1954b8a75509', '9ab6732c-093d-4fee-8efa-b5d2b6ec4d1f', '92e4d416-3a8f-4530-87d0-4c46a9c6df3b', '24a9d6a6-f405-430e-8ba1-2cad9f3a006e', '14351c66-f56c-441c-9b2f-9e9c28dcbf0d', encode(extensions.digest('demo-tracking-1','sha256'),'hex'), 'CONVERTED', 'dsy_usr_D8K2M1', 'PSEUDONYMOUS', now() + interval '1 hour'),
  ('4275ebdd-cc59-43df-a41f-ee9e7f152f25', 'f9aeff9c-1016-4d60-8dbe-6464cc1266cf', 'a852cc19-218c-403a-8a0e-3b5ea4a071a1', '24a9d6a6-f405-430e-8ba1-2cad9f3a006e', '14351c66-f56c-441c-9b2f-9e9c28dcbf0d', encode(extensions.digest('demo-tracking-2','sha256'),'hex'), 'CONVERTED', 'dsy_usr_Q4L9T7', 'PSEUDONYMOUS', now() + interval '1 hour')
on conflict do nothing;

insert into public.provider_orders (
  id, connection_id, handoff_id, provider_order_ref, status, currency,
  order_total_minor, eligible_item_value_minor, publisher_user_ref, placed_at, delivered_at
) values
  ('492f7e7f-13c0-413a-88aa-bc974605ace9', '24a9d6a6-f405-430e-8ba1-2cad9f3a006e', '6b7caedf-7a1d-4b64-8907-1954b8a75509', 'RAPPI-884031', 'DELIVERED', 'ARS', 2899000, 1499000, 'dsy_usr_D8K2M1', '2026-08-16T18:42:12Z', '2026-08-16T19:13:48Z'),
  ('9706d58d-b550-4cb5-8aa0-d0be94cab621', '24a9d6a6-f405-430e-8ba1-2cad9f3a006e', '4275ebdd-cc59-43df-a41f-ee9e7f152f25', 'RAPPI-884029', 'NEAR_DESTINATION', 'ARS', 2458000, 1599000, 'dsy_usr_Q4L9T7', '2026-08-16T18:31:03Z', null)
on conflict do nothing;

insert into public.provider_order_items (
  provider_order_id, provider_product_id, name, category, quantity,
  unit_amount_minor, total_amount_minor, eligible_for_revenue_share
) values
  ('492f7e7f-13c0-413a-88aa-bc974605ace9', 'sku_toy_story_cup', 'Vaso coleccionable Toy Story', 'merchandising', 1, 1499000, 1499000, true),
  ('492f7e7f-13c0-413a-88aa-bc974605ace9', 'sku_popcorn_01', 'Pochoclos clásicos', 'snacks', 1, 659000, 659000, false),
  ('9706d58d-b550-4cb5-8aa0-d0be94cab621', 'sku_moana_tumbler', 'Vaso Moana', 'merchandising', 1, 1599000, 1599000, true)
on conflict do nothing;
