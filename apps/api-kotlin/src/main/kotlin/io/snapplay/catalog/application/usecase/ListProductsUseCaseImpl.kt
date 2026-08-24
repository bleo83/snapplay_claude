package io.snapplay.catalog.application.usecase

import io.snapplay.catalog.application.port.input.ListProductsUseCase
import io.snapplay.catalog.application.port.output.CatalogRepository
import io.snapplay.catalog.application.port.output.ProductFilters
import io.snapplay.catalog.domain.CatalogProduct
import io.snapplay.common.PageResult
import io.snapplay.identity.RequestPrincipal
import org.springframework.stereotype.Service

@Service
class ListProductsUseCaseImpl(
    private val catalogRepository: CatalogRepository,
) : ListProductsUseCase {
    override fun list(
        principal: RequestPrincipal,
        filters: ProductFilters,
        limit: Int,
        cursor: String?,
    ): PageResult<CatalogProduct> = catalogRepository.findProducts(principal.organizationId, filters, limit, cursor)
}
