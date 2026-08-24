package io.snapplay.catalog.application.port.input

import io.snapplay.catalog.application.port.output.ProductFilters
import io.snapplay.catalog.domain.CatalogProduct
import io.snapplay.common.PageResult
import io.snapplay.identity.RequestPrincipal

interface ListProductsUseCase {
    fun list(
        principal: RequestPrincipal,
        filters: ProductFilters,
        limit: Int,
        cursor: String?,
    ): PageResult<CatalogProduct>
}
