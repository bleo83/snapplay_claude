# Snap Play — repository guide

Orchestration platform for the Disney × Rappi pilot. npm workspaces (`apps/*`, `packages/*`)
plus a standalone Gradle service that is the real centre of gravity.

## Active vs legacy — read this first

| Path | Stack | Status |
|------|-------|--------|
| `apps/api-kotlin/` | Kotlin + Spring Boot, JDBC, Flyway | **Active.** The API. Owns its own schema. |
| `apps/backoffice/` | Next.js 16 + React 19, Supabase auth | **Active.** Mid-migration to the Kotlin API. |
| `infra/` | Terraform, AWS `sa-east-1` | **Active.** |
| `.github/workflows/` | CI + deploy | **Active.** |
| `apps/api/` | Fastify + TypeScript | **Legacy.** Being replaced by `apps/api-kotlin`. |
| `supabase/migrations/` | Supabase SQL | **Legacy.** Schema for the Fastify stack. |
| `packages/contracts/` | Zod schemas | **Legacy.** Consumed by the backoffice and the Fastify API. |
| `api/*.yaml` | Hand-written OpenAPI/AsyncAPI | **Legacy.** Superseded by springdoc — see below. |

### Do not modify the legacy paths

`apps/api/`, `supabase/`, `packages/contracts/`, and `api/*.yaml` are retained for reference
during the cutover. Untouched since the Aug 2026 bootstrap. Do not "fix", refactor, or update
them to match new work in the Kotlin API — divergence is expected and intentional.

If a task seems to require changing one of them, stop and ask. The likely answer is that the
work belongs in `apps/api-kotlin/` or `apps/backoffice/` instead.

## The API contract

The authoritative contract is the **springdoc-generated spec at `/api/openapi.json`**, produced
from the Kotlin controllers and DTOs. Not `api/openapi.yaml` (stale), not `packages/contracts/`
(stale). See `apps/api-kotlin/docs/adr-api-compatibility.md` for the versioning and
breaking-change policy — it is binding on all API work.

## Database

Two unrelated schemas. Do not confuse them:

- `apps/api-kotlin/src/main/resources/db/migration/` — Flyway, `V1`–`V15`, **the live schema**
- `supabase/migrations/` — legacy, single file, the Fastify stack

## Conventions

- **Branches:** `<type>/sna-<id>-<short-slug>` where type is `feat` | `fix` | `refactor`
  (e.g. `feat/sna-18-publish-workflow`). Never author names or other prefixes.
- **Commits:** `<type>(SNA-<id>): <summary>` (e.g. `feat(SNA-42): AWS infrastructure as code`).
- **Repo tooling with no ticket** uses `chore/<short-slug>` and `chore: <summary>` — no SNA id.
  Reserved for developer tooling and configuration; all product work carries a ticket.
- Directory-scoped guidance lives in `apps/api-kotlin/CLAUDE.md`, `apps/backoffice/AGENTS.md`,
  and `infra/CLAUDE.md`. The narrowest file wins.

## Agents

Specialised agents live in `.claude/agents/`. Each owns a directory boundary:

| Agent | Owns |
|-------|------|
| `kotlin-api` | `apps/api-kotlin/**` including Flyway migrations |
| `api-contract` | The OpenAPI contract and the ADR's compatibility policy (review-first) |
| `infra` | `infra/**` and `.github/workflows/**` — plans, never applies |
| `backoffice` | `apps/backoffice/**` |
| `security-review` | Whole repo, read-only |

Agents do not share context. Do not run two write-capable agents against the same directory
concurrently — sequence them, or give each its own worktree.
