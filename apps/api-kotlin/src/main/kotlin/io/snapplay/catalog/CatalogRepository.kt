package io.snapplay.catalog

import io.snapplay.common.PageResult
import java.util.UUID

data class ProductFilters(
    val q: String? = null,
    val category: String? = null,
    val status: ProductStatus? = null,
)

interface CatalogRepository {
    fun findProducts(
        organizationId: UUID,
        filters: ProductFilters,
        limit: Int,
        cursor: String?,
    ): PageResult<CatalogProduct>
}
