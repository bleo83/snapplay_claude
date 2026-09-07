package io.snapplay.experience.application.port.output

import java.util.UUID

/** Cross-BC port: validates store/category in the catalog without exposing catalog internals to the experience BC. */
interface CatalogValidationPort {
    data class StoreCheck(val exists: Boolean, val active: Boolean, val stale: Boolean, val internalId: UUID?)

    data class CategoryCheck(val exists: Boolean, val active: Boolean, val stale: Boolean, val linkedToStore: Boolean)

    fun checkStore(
        connectionId: UUID,
        providerStoreId: String,
    ): StoreCheck

    fun checkCategoryForStore(
        connectionId: UUID,
        providerCategoryId: String,
        storeInternalId: UUID,
    ): CategoryCheck
}
