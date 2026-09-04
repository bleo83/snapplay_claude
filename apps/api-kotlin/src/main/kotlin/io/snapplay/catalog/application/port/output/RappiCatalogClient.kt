package io.snapplay.catalog.application.port.output

import io.snapplay.catalog.domain.RappiCatalogPage
import java.util.UUID

interface RappiCatalogClient {
    fun fetchStores(
        connectionId: UUID,
        cursor: String? = null,
    ): RappiCatalogPage
}
