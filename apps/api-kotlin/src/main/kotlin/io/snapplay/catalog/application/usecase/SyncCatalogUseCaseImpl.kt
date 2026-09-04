package io.snapplay.catalog.application.usecase

import io.snapplay.catalog.application.port.input.SyncCatalogUseCase
import io.snapplay.catalog.application.port.output.CategoryRepository
import io.snapplay.catalog.application.port.output.RappiCatalogClient
import io.snapplay.catalog.application.port.output.StoreRepository
import io.snapplay.catalog.domain.CatalogSyncResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class SyncCatalogUseCaseImpl(
    private val rappiClient: RappiCatalogClient,
    private val storeRepo: StoreRepository,
    private val categoryRepo: CategoryRepository,
) : SyncCatalogUseCase {
    private val log = LoggerFactory.getLogger(SyncCatalogUseCaseImpl::class.java)

    override fun sync(connectionId: UUID): CatalogSyncResult {
        val syncedAt = Instant.now()
        var storesUpserted = 0
        var categoriesUpserted = 0
        var relationsCreated = 0
        var cursor: String? = null

        log.info("Starting catalog sync for connection={}", connectionId)

        do {
            val page = rappiClient.fetchStores(connectionId, cursor)

            for (storeSnapshot in page.stores) {
                val storeId =
                    storeRepo.upsert(
                        connectionId = connectionId,
                        providerStoreId = storeSnapshot.providerStoreId,
                        name = storeSnapshot.name,
                        country = storeSnapshot.country,
                        syncedAt = syncedAt,
                    )
                storesUpserted++

                for (catSnapshot in storeSnapshot.categories) {
                    val categoryId =
                        categoryRepo.upsert(
                            connectionId = connectionId,
                            providerCategoryId = catSnapshot.providerCategoryId,
                            name = catSnapshot.name,
                            syncedAt = syncedAt,
                        )
                    categoriesUpserted++

                    categoryRepo.linkToStore(storeId, categoryId)
                    relationsCreated++
                }
            }

            cursor = page.nextCursor
        } while (cursor != null)

        // Mark stores/categories not seen in this sync as STALE
        val storesDeactivated = storeRepo.markStale(connectionId, syncedAt)
        val categoriesDeactivated = categoryRepo.markStale(connectionId, syncedAt)

        log.info(
            "Catalog sync complete connection={}: stores={} categories={} relations={} stale_stores={} stale_categories={}",
            connectionId,
            storesUpserted,
            categoriesUpserted,
            relationsCreated,
            storesDeactivated,
            categoriesDeactivated,
        )

        return CatalogSyncResult(
            connectionId = connectionId,
            storesUpserted = storesUpserted,
            categoriesUpserted = categoriesUpserted,
            relationsCreated = relationsCreated,
            storesDeactivated = storesDeactivated,
            categoriesDeactivated = categoriesDeactivated,
        )
    }
}
