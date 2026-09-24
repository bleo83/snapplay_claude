# contracts — LEGACY

Zod schemas and demo fixtures written for the Fastify API in `apps/api/`. Both are being
replaced by the Kotlin API in `apps/api-kotlin/`.

**Do not add new schemas here.** The authoritative API contract is the springdoc-generated
spec at `/api/openapi.json`, produced from the Kotlin controllers. See
`apps/api-kotlin/docs/adr-api-compatibility.md`.

## Why it still exists

`apps/backoffice/` imports it for the `demo*` fixtures that power demo mode, and for types on
screens not yet migrated. Removing it would break the backoffice today.

## If you are tempted to edit it

Ask first. The likely correct action is one of:

- The type belongs in the Kotlin API → add it there, let springdoc document it.
- A backoffice screen needs a type for a migrated endpoint → derive it from the Kotlin API's
  contract in the backoffice, not here.
- A demo fixture needs updating → that is the one legitimate reason to touch this package.

Consumers: `apps/backoffice/` (active), `apps/api/` (legacy).
