package io.snapplay.catalog.domain

import java.util.UUID

enum class ProductStatus { ACTIVE, INACTIVE }

data class CatalogProduct(
    val id: UUID,
    val providerProductId: String,
    val name: String,
    val description: String?,
    val imageUrl: String,
    val brand: String?,
    val categories: List<String>,
    val ageRestricted: Boolean,
    val referencePriceMinor: Long?,
    val currency: String?,
    val status: ProductStatus,
)
