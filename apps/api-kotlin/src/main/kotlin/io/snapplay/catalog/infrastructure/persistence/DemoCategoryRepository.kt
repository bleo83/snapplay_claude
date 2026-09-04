package io.snapplay.catalog.infrastructure.persistence

import io.snapplay.catalog.application.port.output.CategoryRepository
import io.snapplay.catalog.domain.CatalogEntityStatus
import io.snapplay.catalog.domain.ProviderCategory
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
class DemoCategoryRepository : CategoryRepository {
    private val byId = ConcurrentHashMap<UUID, ProviderCategory>()
    private val byKey = ConcurrentHashMap<String, UUID>() // "connectionId:providerCategoryId" → id
    private val storeLinks = ConcurrentHashMap.newKeySet<Pair<UUID, UUID>>() // (storeId, categoryId)

    fun clear() {
        byId.clear()
        byKey.clear()
        storeLinks.clear()
    }

    fun all(): Collection<ProviderCategory> = byId.values

    override fun upsert(
        connectionId: UUID,
        providerCategoryId: String,
        name: String,
        syncedAt: Instant,
    ): UUID {
        val key = "$connectionId:$providerCategoryId"
        val existing = byKey[key]?.let { byId[it] }
        return if (existing != null) {
            byId[existing.id] = existing.copy(name = name, status = CatalogEntityStatus.ACTIVE, lastSyncedAt = syncedAt)
            existing.id
        } else {
            val id = UUID.randomUUID()
            byId[id] = ProviderCategory(id, connectionId, providerCategoryId, name, CatalogEntityStatus.ACTIVE, syncedAt, Instant.now())
            byKey[key] = id
            id
        }
    }

    override fun listByStore(
        storeId: UUID,
        status: CatalogEntityStatus?,
        limit: Int,
        cursor: String?,
    ): PageResult<ProviderCategory> {
        val linkedCategoryIds = storeLinks.filter { it.first == storeId }.map { it.second }.toSet()
        val cursorPair = cursor?.let { Cursor.decode(it) }
        return byId.values
            .filter { it.id in linkedCategoryIds }
            .filter { status == null || it.status == status }
            .sortedWith(compareByDescending<ProviderCategory> { it.createdAt }.thenByDescending { it.id })
            .filter { c ->
                cursorPair?.let { (ts, id) -> c.createdAt < ts || (c.createdAt == ts && c.id < id) } ?: true
            }
            .take(limit + 1)
            .toPageResult(limit, { it.createdAt }, { it.id }, { it })
    }

    override fun markStale(
        connectionId: UUID,
        cutoff: Instant,
    ): Int {
        var count = 0
        byId.replaceAll { _, cat ->
            if (cat.connectionId == connectionId && cat.status == CatalogEntityStatus.ACTIVE && cat.lastSyncedAt.isBefore(cutoff)) {
                count++
                cat.copy(status = CatalogEntityStatus.STALE)
            } else {
                cat
            }
        }
        return count
    }

    override fun linkToStore(
        storeId: UUID,
        categoryId: UUID,
    ) {
        storeLinks.add(storeId to categoryId)
    }
}
