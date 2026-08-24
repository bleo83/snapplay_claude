# api-kotlin — Agent Guidelines

## Architecture
Hexagonal architecture. Keep layers separate:
- `domain/` — pure data classes, no framework dependencies
- `application/port/input/` — use case interfaces
- `application/port/output/` — repository/query interfaces
- `application/usecase/` — business logic (validation, orchestration)
- `infrastructure/` — Spring, JDBC, web controllers

Business logic belongs in use cases, not repositories. Repositories are pure persistence.

## JDBC / SQL

**Never interpolate dynamic values into SQL strings.** All user-controlled or runtime values must go through `?` parameterized placeholders.

```kotlin
// WRONG — SQL injection risk
append("AND status = '${filters.status}'")

// RIGHT
append("AND status = ?")
// ... then add(filters.status.name) to the params list
```

When a `buildString` query method is flagged by SonarLint as "unsafe SQL string", verify that the SQL string contains only static literals and all values flow through params. If confirmed safe, suppress with:
```kotlin
// sql is built exclusively from static string literals; all dynamic values use ? params — no injection risk
@Suppress("SqlSourceToSinkFlow")
```

## SonarLint suppressions

Apply `@Suppress` at the narrowest possible scope. Always include a comment explaining why.

| Rule | When to suppress |
|------|-----------------|
| `CognitiveComplexMethod` | Query builder methods with 3+ optional filters where splitting would scatter tightly coupled SQL/params logic |
| `USELESS_IS_CHECK` | Kotlin nullable null-checks flagged as redundant — SonarLint's Java-based engine doesn't always track `String?` through lambda contexts correctly |
| `SqlSourceToSinkFlow` | Dynamic `buildString` SQL confirmed to use only static literals + `?` params |

Never suppress without a comment. Never suppress at file or class level.

## Pagination

All list endpoints use keyset (cursor) pagination via `PageResult<T>` and `Cursor.encode/decode`.

- Fetch `limit + 1` rows to detect next page
- Build cursor from `(created_at DESC, id DESC)` of the last item
- Use `toPageResult(limit, getCreatedAt, getId, mapItem)` extension — do not inline the pagination block

Each JDBC row mapper uses a private `XxxRow(val entity: X, val createdAt: Instant)` to carry the timestamp needed for cursor encoding without polluting the domain type.

## Idempotency

Critical mutations (`POST /v1/experiences`, `POST /v1/partner/events`) are protected by `IdempotencyFilter`:

- Client sends `Idempotency-Key: <uuid>` header
- Filter checks `idempotency_keys` table by `(key, endpoint)` within TTL (`snapplay.idempotency-ttl-hours`, default 24h)
- First request: inserts an in-progress row (NULL status_code), executes chain, updates row with captured response
- Duplicate within TTL + complete: replays cached status + body without re-executing
- Duplicate within TTL + in-progress: returns `409 Conflict`
- Filter is disabled in demo mode (`@ConditionalOnProperty snapplay.demo=false`)

To add a new endpoint to idempotency protection, add its path to `IDEMPOTENT_PATHS` in `IdempotencyFilter`.

## Style

- ktlint enforced via `./gradlew ktlintCheck`. Run `./gradlew ktlintFormat` to auto-fix.
- `max_line_length = 160` for `*.kt`; disabled for test files (long SQL strings).
- Keep `@Suppress` annotations on their own line above the suppressed statement.
