package io.snapplay.catalog.application.port.output

import io.snapplay.catalog.domain.CatalogEntityStatus
import io.snapplay.catalog.domain.ProviderCategory
import io.snapplay.common.PageResult
import java.time.Instant
import java.util.UUID

interface CategoryRepository {
    fun upsert(
        connectionId: UUID,
        providerCategoryId: String,
        name: String,
        syncedAt: Instant,
    ): UUID

    /** Lists categories related to [storeId] via the store_categories junction table. */
    fun listByStore(
        storeId: UUID,
        status: CatalogEntityStatus?,
        limit: Int,
        cursor: String?,
    ): PageResult<ProviderCategory>

    fun markStale(
        connectionId: UUID,
        cutoff: Instant,
    ): Int

    /** Creates the store-category relation if it doesn't exist. */
    fun linkToStore(
        storeId: UUID,
        categoryId: UUID,
    )
}
