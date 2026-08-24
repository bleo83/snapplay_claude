package io.snapplay.common

import java.time.Instant
import java.util.Base64
import java.util.UUID

object Cursor {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(
        createdAt: Instant,
        id: UUID,
    ): String = encoder.encodeToString("${createdAt.toEpochMilli()}:$id".toByteArray())

    fun decode(cursor: String): Pair<Instant, UUID>? =
        runCatching {
            val decoded = String(decoder.decode(cursor))
            val colon = decoded.indexOf(':')
            Instant.ofEpochMilli(decoded.substring(0, colon).toLong()) to UUID.fromString(decoded.substring(colon + 1))
        }.getOrNull()
}

fun <R, T> List<R>.toPageResult(
    limit: Int,
    getCreatedAt: (R) -> Instant,
    getId: (R) -> UUID,
    mapItem: (R) -> T,
): PageResult<T> {
    val hasMore = size > limit
    val page = if (hasMore) take(limit) else this
    val nextCursor = if (hasMore) Cursor.encode(getCreatedAt(page.last()), getId(page.last())) else null
    return PageResult(items = page.map(mapItem), nextCursor = nextCursor)
}
