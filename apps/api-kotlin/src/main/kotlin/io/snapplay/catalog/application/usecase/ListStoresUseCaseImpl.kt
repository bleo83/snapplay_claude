package io.snapplay.catalog.application.usecase

import io.snapplay.catalog.application.port.input.ListStoresUseCase
import io.snapplay.catalog.application.port.output.StoreRepository
import io.snapplay.catalog.domain.CatalogEntityStatus
import io.snapplay.catalog.domain.ProviderStore
import io.snapplay.common.PageResult
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ListStoresUseCaseImpl(
    private val storeRepo: StoreRepository,
) : ListStoresUseCase {
    override fun list(
        connectionId: UUID,
        status: CatalogEntityStatus?,
        limit: Int,
        cursor: String?,
    ): PageResult<ProviderStore> = storeRepo.list(connectionId, status, limit, cursor)
}
