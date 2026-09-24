# ADR: API Compatibility and Deprecation Policy

**Status:** Accepted
**Date:** 2026-08-25
**Related:** SNA-14

---

## Context

Snap Play's API is consumed by external partners (Rappi) and internal backoffice tooling. Unannounced breaking changes would break partner integrations and require coordinated deployments. We need a clear, minimal policy that allows the API to evolve without constantly breaking consumers.

---

## Decision

### Versioning

- The current API version is **v1**, expressed in the URL path prefix `/v1/`.
- A new major version (`/v2/`) is introduced only when a breaking change cannot be avoided and a migration window is required.
- Minor, additive changes (new fields, new endpoints, new optional query params) are made directly in the current version without a version bump.

### What counts as a breaking change

The following require a deprecation window before removal:

| Change | Breaking? |
|--------|-----------|
| Removing a field from a response | ✅ Yes |
| Renaming a field | ✅ Yes |
| Changing a field's type or format | ✅ Yes |
| Removing an endpoint | ✅ Yes |
| Making an optional request field required | ✅ Yes |
| Narrowing an enum (removing a value) | ✅ Yes |
| Adding a new field to a response | ❌ No |
| Adding a new optional request field | ❌ No |
| Adding a new endpoint | ❌ No |
| Widening an enum (adding a value) | ❌ No — but document it |
| Stricter validation on existing fields | ⚠️ Case-by-case |

### Deprecation process

1. **Mark in the spec** — add `deprecated: true` to the operation or field in springdoc via `@Deprecated` on the controller method or `@Schema(deprecated = true)` on the DTO field.
2. **Add `Deprecation` header** — the response includes `Deprecation: <RFC 7231 date>` and `Sunset: <RFC 7231 date>` headers for deprecated endpoints.
3. **Minimum notice period** — at least **30 days** for internal consumers; **90 days** for external partners, communicated via the partner changelog.
4. **Removal** — only after the Sunset date has passed and no active traffic is observed on the deprecated path.

### Sunset header convention

```
Deprecation: Sat, 01 Nov 2026 00:00:00 GMT
Sunset: Mon, 01 Dec 2026 00:00:00 GMT
```

These are added manually in the controller for deprecated endpoints until a global `DeprecationFilter` is warranted.

### OpenAPI spec as the contract

The springdoc-generated spec served at `/api/openapi.json` is the authoritative contract. It is generated
from the Kotlin controllers and DTOs — `api/openapi.yaml` and `packages/contracts/` are stale and are not
the contract.

Because the spec is generated from the code, it can never disagree with the code: rename a DTO field and
the spec renames with it. Detecting a breaking change therefore requires comparing the generated spec
against the *previously released* contract. Two tests do that, and they answer different questions:

| Test | Question |
|------|----------|
| `OpenApiContractTest` | Is the spec a valid OpenAPI 3 document, and does each endpoint's live response match the shape it declares? |
| `OpenApiSnapshotTest` | Has the contract changed in a way that breaks existing consumers? |

#### The snapshot

`src/test/resources/openapi/openapi-snapshot.json` is a committed copy of the released contract. It is
written pretty-printed with object keys sorted recursively, so regenerating it produces a small, readable
diff instead of a reshuffled file. It currently covers every documented path, not a hand-maintained subset.

`OpenApiSnapshotTest` runs on every PR, loads the snapshot, fetches the generated spec, and fails on each
breaking change from the table above:

- a path in the snapshot that is missing from the generated spec;
- a `GET`/`POST`/`PATCH`/`PUT`/`DELETE` operation removed from a path that still exists;
- a property removed or renamed in a response schema (a rename reads as a removal plus an added field);
- an enum value removed, in a response, a request body, or a parameter;
- a request field or parameter that was optional and is now required, or a new required request field.

Additive changes — new paths, new operations, new optional fields, new enum values — pass. Per the table
above they are explicitly non-breaking, and a guardrail that cries wolf on them gets disabled.

Failures name the exact operation, field, and ADR rule, for example:

```
BREAKING API CHANGE — ADR rule "Removing a field from a response / Renaming a field"
  - GET /v1/experiences → response 200 (*/*): property `nextCursor` was removed or renamed
```

#### Updating the snapshot

The snapshot is never rewritten automatically on a mismatch. Regenerating it is a deliberate act that shows
up in the pull request diff:

```bash
./gradlew test --tests '*OpenApiSnapshotTest' -Dopenapi.snapshot.update=true
git add src/test/resources/openapi/openapi-snapshot.json
```

For an additive change, regenerating is optional housekeeping — it keeps the snapshot current so later
diffs stay small.

#### When the test fails

A failure means the change breaks Rappi or the backoffice. Regenerating the snapshot to make it green
silently ships that break. Instead:

1. If the break was unintentional, fix the code — keep the old field or enum value.
2. If the removal is intended, run the deprecation process above: mark it deprecated in the spec, ship the
   `Deprecation`/`Sunset` headers, and wait out the 30/90-day notice period. The snapshot stays as-is during
   the window, because the field is still part of the contract.
3. Only in the PR that actually removes the field, after the Sunset date, regenerate the snapshot. That PR's
   diff is the record of the removal, and reviewers must approve it.

### Error responses

Error responses always conform to [RFC 7807 Problem Details](https://www.rfc-editor.org/rfc/rfc7807):

```json
{
  "type": "https://snapplay.io/problems/<slug>",
  "title": "<human-readable summary>",
  "status": <HTTP status code>,
  "detail": "<optional machine-readable detail>",
  "request_id": "<UUID from X-Request-Id>",
  "errors": [{ "field": "...", "message": "..." }]
}
```

The `type`, `title`, `status`, and `request_id` fields are always present. `errors` is present on 422 responses. This shape is stable and will not change without a major version bump.

---

## Consequences

- Partners can safely ignore unknown fields in responses (additive changes are free).
- Breaking changes are rare but when required, consumers have at minimum 30–90 days to adapt.
- `OpenApiSnapshotTest` acts as an automated safety net — any removed path, operation, field, or enum value, and any newly required request field, triggers a CI failure before the code merges.
