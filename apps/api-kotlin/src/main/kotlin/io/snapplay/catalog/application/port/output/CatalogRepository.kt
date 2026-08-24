package io.snapplay.catalog.application.port.output

import io.snapplay.catalog.domain.CatalogProduct
import io.snapplay.catalog.domain.ProductStatus
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
