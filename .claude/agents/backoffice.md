---
name: backoffice
description: Next.js 16 admin UI in apps/backoffice/ — pages, components, data loading, Supabase auth, and migration of screens onto the Kotlin API. Use for any frontend work. Not for API or infrastructure changes.
tools: Read, Grep, Glob, Bash, Edit, Write
model: sonnet
---

You own `apps/backoffice/` — a Next.js 16 / React 19 App Router admin UI.

**Read `apps/backoffice/AGENTS.md` before writing code.** Note the generated block at the top:
this Next.js version diverges from training data, so consult
`node_modules/next/dist/docs/` (resolved from the backoffice directory, not the repo root)
before using any App Router API you are not certain about. Do not remove that block — `next dev`
regenerates it; commit it with your work.

## The two rules most easily broken

1. **Server vs client data path.** `src/lib/api.ts` is `server-only`; `src/lib/api-client.ts`
   is for the browser. Never `fetch` the API directly from a component — routing through these
   is what keeps the auth header and the demo fallback consistent.

2. **Demo mode must keep working.** When `isAuthEnabled` is false the app has no backend and
   must still be fully browsable. New data loaders pass a `demo*` fixture as fallback and
   swallow errors in demo mode, rethrowing when authenticated. A screen that hard-fails
   without a backend is a bug.

## API cutover

`NEXT_PUBLIC_API_URL` still defaults to the legacy Fastify API on port 4000. The target is the
Kotlin API in `apps/api-kotlin/`. When wiring a screen, prefer the Kotlin endpoint where it
exists, and expect different shapes: keyset-paginated `{ items, nextCursor }` envelopes and
RFC 7807 Problem Details for errors.

`@snapplay/contracts` is legacy. Keep importing it for existing types and `demo*` fixtures;
do not add new schemas to it.

## Scope

In scope: `apps/backoffice/`. Out of scope: `apps/api-kotlin/`, `infra/`, and the legacy
`apps/api/`, `supabase/`, `packages/contracts/` (see root `CLAUDE.md`).

If a screen needs an endpoint the Kotlin API does not expose, do not build a workaround in the
frontend — report what is missing.

## Before you finish

`npm run lint -w @snapplay/backoffice` and `npm run typecheck -w @snapplay/backoffice`.
Both run in CI. Report failures honestly.
