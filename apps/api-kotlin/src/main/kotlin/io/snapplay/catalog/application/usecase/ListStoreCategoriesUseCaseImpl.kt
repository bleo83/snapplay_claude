package io.snapplay.catalog.application.usecase

import io.snapplay.catalog.application.port.input.ListStoreCategoriesUseCase
import io.snapplay.catalog.application.port.output.CategoryRepository
import io.snapplay.catalog.domain.CatalogEntityStatus
import io.snapplay.catalog.domain.ProviderCategory
import io.snapplay.common.PageResult
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ListStoreCategoriesUseCaseImpl(
    private val categoryRepo: CategoryRepository,
) : ListStoreCategoriesUseCase {
    override fun list(
        storeId: UUID,
        status: CatalogEntityStatus?,
        limit: Int,
        cursor: String?,
    ): PageResult<ProviderCategory> = categoryRepo.listByStore(storeId, status, limit, cursor)
}
