package io.snapplay.experience.infrastructure.persistence

import io.snapplay.catalog.domain.CatalogEntityStatus
import io.snapplay.catalog.infrastructure.persistence.DemoCategoryRepository
import io.snapplay.catalog.infrastructure.persistence.DemoStoreRepository
import io.snapplay.experience.application.port.output.CatalogValidationPort
import io.snapplay.experience.application.port.output.CatalogValidationPort.CategoryCheck
import io.snapplay.experience.application.port.output.CatalogValidationPort.StoreCheck
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoCatalogValidationPort(
    private val storeRepo: DemoStoreRepository,
    private val categoryRepo: DemoCategoryRepository,
) : CatalogValidationPort {
    override fun checkStore(
        connectionId: UUID,
        providerStoreId: String,
    ): StoreCheck {
        val store =
            storeRepo.all().firstOrNull { it.connectionId == connectionId && it.providerStoreId == providerStoreId }
                ?: return StoreCheck(exists = false, active = false, stale = false, internalId = null)
        return StoreCheck(
            exists = true,
            active = store.status == CatalogEntityStatus.ACTIVE,
            stale = store.status == CatalogEntityStatus.STALE,
            internalId = store.id,
        )
    }

    override fun checkCategoryForStore(
        connectionId: UUID,
        providerCategoryId: String,
        storeInternalId: UUID,
    ): CategoryCheck {
        val category =
            categoryRepo.all().firstOrNull { it.connectionId == connectionId && it.providerCategoryId == providerCategoryId }
                ?: return CategoryCheck(exists = false, active = false, stale = false, linkedToStore = false)

        // Check if category is linked to the store via listByStore
        val linked = categoryRepo.listByStore(storeInternalId, null, 1000, null).items.any { it.id == category.id }

        return CategoryCheck(
            exists = true,
            active = category.status == CatalogEntityStatus.ACTIVE,
            stale = category.status == CatalogEntityStatus.STALE,
            linkedToStore = linked,
        )
    }
}
