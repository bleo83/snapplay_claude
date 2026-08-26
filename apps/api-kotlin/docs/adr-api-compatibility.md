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

- The spec at `/api/openapi.json` is the authoritative contract.
- `OpenApiContractTest` runs on every PR and fails if the spec no longer documents an endpoint that was previously present.
- Reviewers must approve any PR that removes or renames a documented field.

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
- The `OpenApiContractTest` acts as an automated safety net — any removed path or missing schema triggers a CI failure before code merges.
