package io.snapplay.catalog.application.port.input

import io.snapplay.catalog.domain.CatalogEntityStatus
import io.snapplay.catalog.domain.ProviderCategory
import io.snapplay.common.PageResult
import java.util.UUID

interface ListStoreCategoriesUseCase {
    /** Lists categories that are valid for the given store (enforces the store-category relationship). */
    fun list(
        storeId: UUID,
        status: CatalogEntityStatus? = null,
        limit: Int = 50,
        cursor: String? = null,
    ): PageResult<ProviderCategory>
}
