package io.snapplay.catalog.infrastructure.persistence

import io.snapplay.catalog.application.port.output.CategoryRepository
import io.snapplay.catalog.domain.CatalogEntityStatus
import io.snapplay.catalog.domain.ProviderCategory
import io.snapplay.common.Cursor
import io.snapplay.common.PageResult
import io.snapplay.common.toPageResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcCategoryRepository(
    private val jdbc: JdbcTemplate,
) : CategoryRepository {
    override fun upsert(
        connectionId: UUID,
        providerCategoryId: String,
        name: String,
        syncedAt: Instant,
    ): UUID =
        jdbc.queryForObject(
            """
            INSERT INTO provider_categories (connection_id, provider_category_id, name, status, last_synced_at)
            VALUES (?, ?, ?, 'ACTIVE', ?)
            ON CONFLICT (connection_id, provider_category_id)
            DO UPDATE SET name = EXCLUDED.name, status = 'ACTIVE',
                          last_synced_at = EXCLUDED.last_synced_at, updated_at = now()
            RETURNING id
            """.trimIndent(),
            UUID::class.java,
            connectionId,
            providerCategoryId,
            name,
            Timestamp.from(syncedAt),
        )!!

    @Suppress("SqlSourceToSinkFlow") // sql built from static literals; all dynamic values use ? params
    override fun listByStore(
        storeId: UUID,
        status: CatalogEntityStatus?,
        limit: Int,
        cursor: String?,
    ): PageResult<ProviderCategory> {
        val params = mutableListOf<Any>()
        val sql =
            buildString {
                append("SELECT c.id, c.connection_id, c.provider_category_id, c.name, c.status, c.last_synced_at, c.created_at")
                append(" FROM provider_categories c")
                append(" JOIN store_categories sc ON sc.category_id = c.id")
                append(" WHERE sc.store_id = ?")
                params.add(storeId)
                if (status != null) {
                    append(" AND c.status = ?")
                    params.add(status.name)
                }
                cursor?.let { Cursor.decode(it) }?.let { (ts, id) ->
                    append(" AND (c.created_at, c.id) < (?, ?)")
                    params.add(Timestamp.from(ts))
                    params.add(id)
                }
                append(" ORDER BY c.created_at DESC, c.id DESC LIMIT ?")
                params.add(limit + 1)
            }

        return jdbc.query(sql, { rs, _ -> rs.toCategory() }, *params.toTypedArray())
            .toPageResult(limit, { it.createdAt }, { it.id }, { it })
    }

    override fun markStale(
        connectionId: UUID,
        cutoff: Instant,
    ): Int =
        jdbc.update(
            "UPDATE provider_categories SET status = 'STALE', updated_at = now() WHERE connection_id = ? AND status = 'ACTIVE' AND last_synced_at < ?",
            connectionId,
            Timestamp.from(cutoff),
        )

    override fun linkToStore(
        storeId: UUID,
        categoryId: UUID,
    ) {
        jdbc.update(
            "INSERT INTO store_categories (store_id, category_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
            storeId,
            categoryId,
        )
    }

    private fun ResultSet.toCategory() =
        ProviderCategory(
            id = UUID.fromString(getString("id")),
            connectionId = UUID.fromString(getString("connection_id")),
            providerCategoryId = getString("provider_category_id"),
            name = getString("name"),
            status = CatalogEntityStatus.valueOf(getString("status")),
            lastSyncedAt = getTimestamp("last_synced_at").toInstant(),
            createdAt = getTimestamp("created_at").toInstant(),
        )
}
