<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->

# backoffice — Agent Guidelines

Next.js 16 + React 19 App Router. Admin UI for the Snap Play platform.

## Two data paths — pick the right one

| File | Runtime | Use for |
|------|---------|---------|
| `src/lib/api.ts` | **server only** (`import "server-only"`) | Server Components, page-level data loading |
| `src/lib/api-client.ts` | browser | Client Components, form submissions, interactions |

Both attach the Supabase session as `Authorization: Bearer <token>` when auth is enabled.
Never call `fetch` against the API directly from a component — go through one of these so the
auth header and demo fallback stay consistent.

## Demo mode is a first-class state

`isAuthEnabled` in `src/lib/supabase/config.ts` is false when Supabase env vars are missing,
still contain placeholders (`your-project`, `your-publishable-key`), or when
`NEXT_PUBLIC_SNAPPLAY_DEMO_MODE === "true"`.

In demo mode the app must stay fully browsable without a backend:

- `apiGet` in `src/lib/api.ts` swallows errors and returns the `demo*` fixture as fallback
  (1.5s timeout). When auth **is** enabled it rethrows instead.
- `src/proxy.ts` skips the auth redirect entirely.

Any new data-loading function must follow the same shape: pass a demo fallback, let errors
through when authenticated. Do not add a call that hard-fails in demo mode.

## API cutover — in progress

`NEXT_PUBLIC_API_URL` defaults to `http://localhost:4000`, which is the **legacy Fastify API**.
The target is the Kotlin API in `apps/api-kotlin/`. When wiring a new screen, check whether the
endpoint exists there and prefer it. Note the response shapes differ: the Kotlin API returns
keyset-paginated `{ items, nextCursor }` envelopes and RFC 7807 Problem Details on errors.

`@snapplay/contracts` (Zod) is legacy — it typed the Fastify API. It is still imported for the
`demo*` fixtures and existing types, so do not rip it out, but do not add new schemas to it.
New types should follow the Kotlin API's contract. See the root `CLAUDE.md`.

## Auth

Supabase SSR. `src/proxy.ts` gates every route, redirecting unauthenticated users to `/login`
with a `next` param. Cookie handling there is the documented `@supabase/ssr` pattern — if you
touch it, keep `getAll`/`setAll` intact or sessions break silently in Server Components.

## Conventions

- Route folders under `src/app/<feature>/` pair a server `page.tsx` with a
  `<feature>-client.tsx` Client Component when interactivity is needed.
- `next.config.ts` has `transpilePackages: ["@snapplay/contracts"]` — the workspace package
  ships TS, so it must be transpiled. Do not remove.
- Icons come from `lucide-react`. Styling is in `src/app/globals.css` — no CSS framework.
- Run `npm run lint -w @snapplay/backoffice` and `npm run typecheck -w @snapplay/backoffice`
  before committing. Both run in CI.
