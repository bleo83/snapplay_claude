package io.snapplay.catalog

import java.util.UUID

data class ProductFilters(
    val q: String? = null,
    val category: String? = null,
    val status: ProductStatus? = null,
)

fun interface CatalogRepository {
    fun findProducts(organizationId: UUID, filters: ProductFilters): List<CatalogProduct>
}
