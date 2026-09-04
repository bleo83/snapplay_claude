package io.snapplay.catalog.application.port.input

import io.snapplay.catalog.domain.CatalogSyncResult
import java.util.UUID

interface SyncCatalogUseCase {
    /** Pulls stores and categories from the provider and upserts into local catalog. */
    fun sync(connectionId: UUID): CatalogSyncResult
}
