package io.snapplay.catalog.infrastructure.web

import io.snapplay.catalog.application.port.input.ListStoreCategoriesUseCase
import io.snapplay.catalog.application.port.input.ListStoresUseCase
import io.snapplay.catalog.application.port.input.SyncCatalogUseCase
import io.snapplay.catalog.domain.CatalogEntityStatus
import io.snapplay.catalog.domain.CatalogSyncResult
import io.snapplay.catalog.domain.ProviderCategory
import io.snapplay.catalog.domain.ProviderStore
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/v1/catalog")
class StoreCategoryController(
    private val listStoresUseCase: ListStoresUseCase,
    private val listStoreCategoriesUseCase: ListStoreCategoriesUseCase,
    private val syncCatalogUseCase: SyncCatalogUseCase,
) {
    @GetMapping("/stores")
    fun listStores(
        @RequestParam connectionId: UUID,
        @RequestParam(required = false) status: CatalogEntityStatus?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): StoreListResponse {
        val result = listStoresUseCase.list(connectionId, status, limit.coerceIn(1, 200), cursor)
        return StoreListResponse(
            items = result.items.map { it.toDto() },
            nextCursor = result.nextCursor,
        )
    }

    @GetMapping("/stores/{storeId}/categories")
    fun listCategories(
        @PathVariable storeId: UUID,
        @RequestParam(required = false) status: CatalogEntityStatus?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): CategoryListResponse {
        val result = listStoreCategoriesUseCase.list(storeId, status, limit.coerceIn(1, 200), cursor)
        return CategoryListResponse(
            items = result.items.map { it.toDto() },
            nextCursor = result.nextCursor,
        )
    }

    @PostMapping("/sync")
    fun sync(
        @RequestParam connectionId: UUID,
    ): CatalogSyncResult = syncCatalogUseCase.sync(connectionId)

    private fun ProviderStore.toDto() =
        StoreDto(
            id = id,
            providerStoreId = providerStoreId,
            name = name,
            country = country,
            status = status,
        )

    private fun ProviderCategory.toDto() =
        CategoryDto(
            id = id,
            providerCategoryId = providerCategoryId,
            name = name,
            status = status,
        )
}

data class StoreListResponse(
    val items: List<StoreDto>,
    val nextCursor: String?,
)

data class StoreDto(
    val id: UUID,
    val providerStoreId: String,
    val name: String,
    val country: String,
    val status: CatalogEntityStatus,
)

data class CategoryListResponse(
    val items: List<CategoryDto>,
    val nextCursor: String?,
)

data class CategoryDto(
    val id: UUID,
    val providerCategoryId: String,
    val name: String,
    val status: CatalogEntityStatus,
)
