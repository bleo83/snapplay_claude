  ---
Snap Play — Tech Lead Analysis & Plan

The Honest Picture

The codebase is a well-architected but early-stage foundation. ~20% of the OpenAPI surface is implemented. The DB schema is nearly complete. The demo mode gives you a good local story. But there is zero
infrastructure, the critical user-facing flow (QR → handoff → redirect) doesn't exist, and several modules needed for the pilot aren't built.

  ---
What "The Pilot" Actually Requires (End-to-End Flow)

Disney admin configures experience
→ generates QR / smart link
→ user scans QR
→ Snap Play resolves link, creates handoff session
→ redirects user to Rappi storefront
→ user buys
→ Rappi sends order webhook
→ Snap Play records order, calculates fees
→ Disney sees attribution + revenue share

Everything above that isn't built is a pilot blocker. Let's map it.

  ---
Current State vs. Pilot Requirements

┌──────────────────────┬──────────────────┬─────────────────────────────────────────────┐
│         Area         │      Built       │                   Missing                   │
├──────────────────────┼──────────────────┼─────────────────────────────────────────────┤
│ Organization profile │ GET, PATCH       │ POST (create)                               │
├──────────────────────┼──────────────────┼─────────────────────────────────────────────┤
│ Connections          │ Nothing          │ Full CRUD — Disney ↔ Rappi link             │
├──────────────────────┼──────────────────┼─────────────────────────────────────────────┤
│ Content contexts     │ Nothing          │ POST (create movie/channel context)         │
├──────────────────────┼──────────────────┼─────────────────────────────────────────────┤
│ Catalog sync         │ Read-only (demo) │ Rappi catalog pull/import                   │
├──────────────────────┼──────────────────┼─────────────────────────────────────────────┤
│ Experiences          │ Create + List    │ Publish workflow, single GET, update        │
├──────────────────────┼──────────────────┼─────────────────────────────────────────────┤
│ Smart links          │ Nothing          │ Generate, resolve (/r/{shortCode})          │
├──────────────────────┼──────────────────┼─────────────────────────────────────────────┤
│ Handoff sessions     │ Nothing          │ Create, track, expire                       │
├──────────────────────┼──────────────────┼─────────────────────────────────────────────┤
│ Orders               │ Nothing          │ Ingest Rappi webhooks, state machine        │
├──────────────────────┼──────────────────┼─────────────────────────────────────────────┤
│ Contracts            │ Nothing          │ Create, version, approve                    │
├──────────────────────┼──────────────────┼─────────────────────────────────────────────┤
│ Ledger / Settlement  │ Nothing          │ Per-order entries, settlement calc          │
├──────────────────────┼──────────────────┼─────────────────────────────────────────────┤
│ Analytics            │ Nothing          │ Funnel, conversion, GMV                     │
├──────────────────────┼──────────────────┼─────────────────────────────────────────────┤
│ Infrastructure       │ Nothing          │ Docker, CI/CD, cloud deploy, Redis, secrets │
├──────────────────────┼──────────────────┼─────────────────────────────────────────────┤
│ Outbox processor     │ Table exists     │ No publisher, no consumer                   │
└──────────────────────┴──────────────────┴─────────────────────────────────────────────┘

  ---
Tech Debt (Pre-Existing)

These are issues in code that's already written — not new features.

High severity:
1. No pagination — findAll() loads unbounded rows. OpenAPI requires cursor-based pagination. Will blow up in production.
2. No idempotency key handling — all mutations lack the Idempotency-Key header the spec requires. Critical for webhook safety.
3. channel field in experience creation is ignored — the request sends a channel but the code derives it from contextTitle. If they diverge, the stored experience is wrong.
4. Experience versions have no immutability enforcement — the schema supports versioning but there's no publish endpoint, no state machine, and no validation guards before publishing.
5. No outbox processor — the outbox_events table exists and gets written to, but nothing reads from it. Events are being silently dropped.

Medium severity:

6. Catalog, Organization modules still flat — only experience has hexagonal architecture. Inconsistency will make onboarding new devs harder as the codebase grows.
7. Business logic in JdbcExperienceRepository.create() — connection/contract validation is persistence-layer responsibility. It should be in the use case. (This was there before the hexagonal migration — we
   moved the structure but not all the logic.)
8. No request-level tenant isolation enforcement — all queries manually pass organizationId. No RLS activated. One missed filter = data leak.
9. JdbcOrganizationRepository and JdbcCatalogRepository test coverage — Testcontainers tests exist but weren't run in CI (no GitHub Actions workflow configured).

Low severity:

10. DemoExperienceRepository always returns "Disney+" as channel — hardcoded, doesn't reflect the channel from the request. Fine for demos but misleading.
11. No structured logging / trace IDs in service layer — RequestIdFilter adds the header but nothing propagates request_id into log lines for correlation.

  ---
Milestone Plan

Milestone 0 — Foundation (Internal only, ~1 week)

Goal: Any developer can run the full stack locally; CI catches regressions.

- Docker Compose: PostgreSQL + API (snapplay.demo=false)
- GitHub Actions: build + unit tests on PR
- Migrate catalog and organization to hexagonal (align with experience)
- Fix pagination on all list endpoints (cursor-based, default 50)
- Fix idempotency key middleware
- Fix channel field bug in experience creation
- Move connection/contract validation from JdbcExperienceRepository into CreateExperienceUseCaseImpl

Exit criteria: docker compose up → all demo endpoints work; CI is green.

  ---
Milestone 1 — Backoffice Config Flow (~2 weeks)

Goal: Disney admin can configure an experience end-to-end via API. Demoable to stakeholders with Postman/Insomnia.

Build:
- POST /v1/organizations — create org
- POST /v1/connections — create Disney ↔ Rappi connection
- GET /v1/connections/{id} — view connection status
- POST /v1/content-contexts — create movie/series/event context
- POST /v1/catalog/syncs — trigger Rappi catalog pull (stubbed response OK)
- POST /v1/experiences/{id}/versions/{v}/publish — validate + publish (immutable version)
- Experience state machine: DRAFT → IN_REVIEW → PUBLISHED guards
- POST /v1/contracts + POST /v1/contracts/{id}/versions — contract skeleton
- GET /v1/experiences/{id} — single experience retrieve

Exit criteria: Full admin flow runnable via API. Demo script works without touching the DB directly.

  ---
Milestone 2 — QR + Handoff (External demo with Disney, ~2 weeks)

Goal: Real QR code that resolves to a Rappi storefront. This is the moment the pilot becomes tangible.

Build:
- POST /v1/smart-links — generate short code + QR image (PNG or SVG)
- GET /r/{shortCode} — public endpoint, p95 < 250ms:
    - Resolve experience version
    - Validate it's still active/published
    - Create handoff_session record
    - Call Rappi adapter (STORE_DEEPLINK mode first — simplest)
    - Redirect 302 with Cache-Control: no-store
- Rappi STORE_DEEPLINK connector (first adapter)
- Rate limiting on /r/{shortCode}
- Kill switch: pause a smart link without touching the experience

Infrastructure needed here:
- Cloud deployment (Railway / Fly.io for fast iteration, or ECS if you're already AWS)
- PostgreSQL managed (Supabase works — you already have the JWKS URI configured)
- Redis for smart link cache (< 5ms lookups vs < 50ms DB)
- HTTPS + domain (required for QR to work on mobile)

Exit criteria: Print a QR, scan it on a phone, land on Rappi. Record the demo video.

  ---
Milestone 3 — Order Tracking (~2 weeks)

Goal: Rappi order webhooks flow into Snap Play. Disney can see that a purchase happened.

Build:
- POST /v1/partner/events — CloudEvents ingest from Rappi:
    - HMAC signature validation
    - Idempotent deduplication by event_id
    - 202 Accepted immediately
    - Transactional outbox write in same DB transaction
- Outbox processor (scheduled job or Kafka consumer):
    - Reads unprocessed outbox entries
    - Applies order state machine transitions
    - Updates provider_orders + order_status_history
- GET /v1/handoffs/{handoffId}/status — polled by Disney backoffice
- POST /v1/webhook-subscriptions — Disney registers their endpoint
- Outbound webhook delivery to Disney (order milestones, policy-filtered)
- Reconciliation job: daily pull from Rappi to catch missed webhooks

Exit criteria: Simulate a Rappi webhook, verify Disney receives the delivery event within 5 seconds.

  ---
Milestone 4 — Settlement Alpha (~2 weeks)

Goal: Calculate the first settlement statement. Finance can verify the numbers.

Build:
- POST /v1/contracts/{id}/versions/{v}/approve — freeze contract version
- Ledger engine: per-order entry creation when order reaches billable state
- POST /v1/settlements/{id}/calculate — reproducible calculation per period
- GET /v1/settlements — list statements
- Basic finance view: earned, pending, reversed amounts per org
- Four-eyes approval flow for settlement statements

Exit criteria: Run a settlement calculation for the pilot period. Numbers match Rappi's report.

  ---
Infrastructure Priorities (Parallel track)

┌─────────────────────────────────────────────┬──────────────────────────────────────┬──────┐
│                    What                     │                 Why                  │ When │
├─────────────────────────────────────────────┼──────────────────────────────────────┼──────┤
│ Docker Compose                              │ Local dev parity                     │ M0   │
├─────────────────────────────────────────────┼──────────────────────────────────────┼──────┤
│ GitHub Actions CI                           │ Prevent regressions                  │ M0   │
├─────────────────────────────────────────────┼──────────────────────────────────────┼──────┤
│ Managed PostgreSQL (Supabase)               │ Already half-configured              │ M1   │
├─────────────────────────────────────────────┼──────────────────────────────────────┼──────┤
│ Cloud API deployment                        │ Required for QR/mobile               │ M2   │
├─────────────────────────────────────────────┼──────────────────────────────────────┼──────┤
│ Redis                                       │ Smart link latency SLO               │ M2   │
├─────────────────────────────────────────────┼──────────────────────────────────────┼──────┤
│ HTTPS + domain                              │ QR must work on phones               │ M2   │
├─────────────────────────────────────────────┼──────────────────────────────────────┼──────┤
│ Secrets Manager                             │ Partner credentials, webhook secrets │ M2   │
├─────────────────────────────────────────────┼──────────────────────────────────────┼──────┤
│ Structured logging + request_id propagation │ Debug production issues              │ M2   │
├─────────────────────────────────────────────┼──────────────────────────────────────┼──────┤
│ OpenTelemetry traces                        │ Debug latency, error rates           │ M3   │
├─────────────────────────────────────────────┼──────────────────────────────────────┼──────┤
│ Outbox processor infra                      │ Reliable order events                │ M3   │
└─────────────────────────────────────────────┴──────────────────────────────────────┴──────┘
   
---                                                                                                                                                                                                            
What to NOT build yet

- Analytics warehouse (premature — no orders yet)
- Self-service organization onboarding (seed it for pilot)
- Multiple connector adapters (STORE_DEEPLINK only for M2)
- Dispute portal
- Full RLS enforcement (add in M3 as a security hardening pass)
- ORDER_API handoff mode (highest complexity, not needed for pilot)

  ---                                                                                                                                                                                                            
Summary: Where to Start Monday

1. Docker Compose + CI (unblocks everyone, cheap)
2. Fix the 3 bugs (pagination, idempotency, channel field)
3. Migrate catalog + organization to hexagonal (before they grow)
4. Content contexts + connections endpoints (M1 gate — nothing works without them)
5. Experience publish workflow (the QR only makes sense with a published experience)

The critical insight is that M2 (QR scan) is the demo moment that will make the pilot real for Disney. Everything before it is setup. Prioritize getting to that moment fast, even if contracts and settlement
are manual spreadsheets for the first month.                                                                                                                                                                   
