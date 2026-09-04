package io.snapplay.catalog.domain

import java.time.Instant
import java.util.UUID

enum class CatalogEntityStatus { ACTIVE, INACTIVE, STALE }

data class ProviderStore(
    val id: UUID,
    val connectionId: UUID,
    val providerStoreId: String,
    val name: String,
    val country: String,
    val status: CatalogEntityStatus,
    val lastSyncedAt: Instant,
    val createdAt: Instant,
)

data class ProviderCategory(
    val id: UUID,
    val connectionId: UUID,
    val providerCategoryId: String,
    val name: String,
    val status: CatalogEntityStatus,
    val lastSyncedAt: Instant,
    val createdAt: Instant,
)

/** A store snapshot as returned by Rappi's catalog API. */
data class RappiStoreSnapshot(
    val providerStoreId: String,
    val name: String,
    val country: String,
    val categories: List<RappiCategorySnapshot>,
)

data class RappiCategorySnapshot(
    val providerCategoryId: String,
    val name: String,
)

data class RappiCatalogPage(
    val stores: List<RappiStoreSnapshot>,
    val nextCursor: String?,
)

data class CatalogSyncResult(
    val connectionId: UUID,
    val storesUpserted: Int,
    val categoriesUpserted: Int,
    val relationsCreated: Int,
    val storesDeactivated: Int,
    val categoriesDeactivated: Int,
)
