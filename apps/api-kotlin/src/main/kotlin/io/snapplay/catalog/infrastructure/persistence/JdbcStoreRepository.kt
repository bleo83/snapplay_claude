package io.snapplay.catalog.infrastructure.persistence

import io.snapplay.catalog.application.port.output.StoreRepository
import io.snapplay.catalog.domain.CatalogEntityStatus
import io.snapplay.catalog.domain.ProviderStore
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
class JdbcStoreRepository(
    private val jdbc: JdbcTemplate,
) : StoreRepository {
    override fun upsert(
        connectionId: UUID,
        providerStoreId: String,
        name: String,
        country: String,
        syncedAt: Instant,
    ): UUID {
        return jdbc.queryForObject(
            """
            INSERT INTO provider_stores (connection_id, provider_store_id, name, country, status, last_synced_at)
            VALUES (?, ?, ?, ?, 'ACTIVE', ?)
            ON CONFLICT (connection_id, provider_store_id)
            DO UPDATE SET name = EXCLUDED.name, country = EXCLUDED.country,
                          status = 'ACTIVE', last_synced_at = EXCLUDED.last_synced_at, updated_at = now()
            RETURNING id
            """.trimIndent(),
            UUID::class.java,
            connectionId,
            providerStoreId,
            name,
            country,
            Timestamp.from(syncedAt),
        )!!
    }

    @Suppress("SqlSourceToSinkFlow") // sql built from static literals; all dynamic values use ? params
    override fun list(
        connectionId: UUID,
        status: CatalogEntityStatus?,
        limit: Int,
        cursor: String?,
    ): PageResult<ProviderStore> {
        val params = mutableListOf<Any>()
        val sql =
            buildString {
                append("SELECT id, connection_id, provider_store_id, name, country, status, last_synced_at, created_at")
                append(" FROM provider_stores WHERE connection_id = ?")
                params.add(connectionId)
                if (status != null) {
                    append(" AND status = ?")
                    params.add(status.name)
                }
                cursor?.let { Cursor.decode(it) }?.let { (ts, id) ->
                    append(" AND (created_at, id) < (?, ?)")
                    params.add(Timestamp.from(ts))
                    params.add(id)
                }
                append(" ORDER BY created_at DESC, id DESC LIMIT ?")
                params.add(limit + 1)
            }

        return jdbc.query(sql, { rs, _ -> rs.toStore() }, *params.toTypedArray())
            .toPageResult(limit, { it.createdAt }, { it.id }, { it })
    }

    override fun markStale(
        connectionId: UUID,
        cutoff: Instant,
    ): Int =
        jdbc.update(
            "UPDATE provider_stores SET status = 'STALE', updated_at = now() WHERE connection_id = ? AND status = 'ACTIVE' AND last_synced_at < ?",
            connectionId,
            Timestamp.from(cutoff),
        )

    private fun ResultSet.toStore() =
        ProviderStore(
            id = UUID.fromString(getString("id")),
            connectionId = UUID.fromString(getString("connection_id")),
            providerStoreId = getString("provider_store_id"),
            name = getString("name"),
            country = getString("country"),
            status = CatalogEntityStatus.valueOf(getString("status")),
            lastSyncedAt = getTimestamp("last_synced_at").toInstant(),
            createdAt = getTimestamp("created_at").toInstant(),
        )
}
