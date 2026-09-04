package io.snapplay.catalog.infrastructure.persistence

import io.snapplay.catalog.application.port.output.StoreRepository
import io.snapplay.catalog.domain.CatalogEntityStatus
import io.snapplay.catalog.domain.ProviderStore
import io.snapplay.common.Cursor
import io.snapplay.common.PageResult
import io.snapplay.common.toPageResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoStoreRepository : StoreRepository {
    private val byId = ConcurrentHashMap<UUID, ProviderStore>()
    private val byKey = ConcurrentHashMap<String, UUID>() // "connectionId:providerStoreId" → id

    fun clear() {
        byId.clear()
        byKey.clear()
    }

    fun all(): Collection<ProviderStore> = byId.values

    override fun upsert(
        connectionId: UUID,
        providerStoreId: String,
        name: String,
        country: String,
        syncedAt: Instant,
    ): UUID {
        val key = "$connectionId:$providerStoreId"
        val existing = byKey[key]?.let { byId[it] }
        return if (existing != null) {
            val updated = existing.copy(name = name, country = country, status = CatalogEntityStatus.ACTIVE, lastSyncedAt = syncedAt)
            byId[existing.id] = updated
            existing.id
        } else {
            val id = UUID.randomUUID()
            val store = ProviderStore(id, connectionId, providerStoreId, name, country, CatalogEntityStatus.ACTIVE, syncedAt, Instant.now())
            byId[id] = store
            byKey[key] = id
            id
        }
    }

    override fun list(
        connectionId: UUID,
        status: CatalogEntityStatus?,
        limit: Int,
        cursor: String?,
    ): PageResult<ProviderStore> {
        val cursorPair = cursor?.let { Cursor.decode(it) }
        return byId.values
            .filter { it.connectionId == connectionId }
            .filter { status == null || it.status == status }
            .sortedWith(compareByDescending<ProviderStore> { it.createdAt }.thenByDescending { it.id })
            .filter { s ->
                cursorPair?.let { (ts, id) -> s.createdAt < ts || (s.createdAt == ts && s.id < id) } ?: true
            }
            .take(limit + 1)
            .toPageResult(limit, { it.createdAt }, { it.id }, { it })
    }

    override fun markStale(
        connectionId: UUID,
        cutoff: Instant,
    ): Int {
        var count = 0
        byId.replaceAll { _, store ->
            if (store.connectionId == connectionId && store.status == CatalogEntityStatus.ACTIVE && store.lastSyncedAt.isBefore(cutoff)) {
                count++
                store.copy(status = CatalogEntityStatus.STALE)
            } else {
                store
            }
        }
        return count
    }
}
