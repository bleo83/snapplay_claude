package io.snapplay.catalog.application.port.output

import io.snapplay.catalog.domain.CatalogEntityStatus
import io.snapplay.catalog.domain.ProviderStore
import io.snapplay.common.PageResult
import java.time.Instant
import java.util.UUID

interface StoreRepository {
    fun upsert(
        connectionId: UUID,
        providerStoreId: String,
        name: String,
        country: String,
        syncedAt: Instant,
    ): UUID

    fun list(
        connectionId: UUID,
        status: CatalogEntityStatus?,
        limit: Int,
        cursor: String?,
    ): PageResult<ProviderStore>

    /** Marks all stores for [connectionId] not synced since [cutoff] as STALE. Returns count. */
    fun markStale(
        connectionId: UUID,
        cutoff: Instant,
    ): Int
}
