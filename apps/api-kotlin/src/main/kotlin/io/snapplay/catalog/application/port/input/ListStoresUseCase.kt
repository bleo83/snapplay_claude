package io.snapplay.catalog.application.port.input

import io.snapplay.catalog.domain.CatalogEntityStatus
import io.snapplay.catalog.domain.ProviderStore
import io.snapplay.common.PageResult
import java.util.UUID

interface ListStoresUseCase {
    fun list(
        connectionId: UUID,
        status: CatalogEntityStatus? = null,
        limit: Int = 50,
        cursor: String? = null,
    ): PageResult<ProviderStore>
}
