package io.snapplay.catalog

import io.snapplay.common.PageResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoCatalogRepository : CatalogRepository {
    private val products =
        listOf(
            CatalogProduct(
                id = UUID.fromString("71f4fceb-a8be-45c9-bd66-61c9e60fd26f"),
                providerProductId = "sku_toy_story_cup",
                name = "Vaso coleccionable Toy Story",
                description = "Vaso temático de 500 ml, edición Movie Night.",
                imageUrl = "https://placehold.co/640x480/1f6fff/ffffff?text=Toy+Story+Cup",
                brand = "Disney",
                categories = listOf("merchandising", "movie-night"),
                ageRestricted = false,
                referencePriceMinor = 1_499_000,
                currency = "ARS",
                status = ProductStatus.ACTIVE,
            ),
            CatalogProduct(
                id = UUID.fromString("6d32d586-e5ce-41f4-b9c4-7c891ad26f06"),
                providerProductId = "sku_popcorn_01",
                name = "Pochoclos clásicos",
                description = "Pochoclos listos para una noche de película.",
                imageUrl = "https://placehold.co/640x480/f5c451/1d2433?text=Pochoclos",
                brand = "Turbo",
                categories = listOf("snacks", "movie-night"),
                ageRestricted = false,
                referencePriceMinor = 659_000,
                currency = "ARS",
                status = ProductStatus.ACTIVE,
            ),
            CatalogProduct(
                id = UUID.fromString("275f24dd-31c9-41d5-8580-ad83bfa89d61"),
                providerProductId = "sku_moana_tumbler",
                name = "Vaso Moana",
                description = "Vaso reutilizable inspirado en Moana.",
                imageUrl = "https://placehold.co/640x480/10b7b4/ffffff?text=Moana+Tumbler",
                brand = "Disney",
                categories = listOf("merchandising", "family"),
                ageRestricted = false,
                referencePriceMinor = 1_599_000,
                currency = "ARS",
                status = ProductStatus.ACTIVE,
            ),
            CatalogProduct(
                id = UUID.fromString("13591f43-f74b-4bf8-9e7d-766db1bb4b0d"),
                providerProductId = "sku_cola_15l",
                name = "Gaseosa cola 1,5 L",
                description = "Bebida para compartir.",
                imageUrl = "https://placehold.co/640x480/e94235/ffffff?text=Gaseosa",
                brand = "Partner brand",
                categories = listOf("bebidas"),
                ageRestricted = false,
                referencePriceMinor = 399_000,
                currency = "ARS",
                status = ProductStatus.ACTIVE,
            ),
            CatalogProduct(
                id = UUID.fromString("ba60645a-7e51-49e0-b0a7-39c956570e8b"),
                providerProductId = "sku_beer_473",
                name = "Cerveza 473 ml",
                description = "Venta sujeta a edad, horario y jurisdicción.",
                imageUrl = "https://placehold.co/640x480/d88b22/ffffff?text=Cerveza",
                brand = "Partner brand",
                categories = listOf("bebidas", "adultos"),
                ageRestricted = true,
                referencePriceMinor = 279_000,
                currency = "ARS",
                status = ProductStatus.ACTIVE,
            ),
            CatalogProduct(
                id = UUID.fromString("e91c3430-720f-4310-ad83-0bda12fd741c"),
                providerProductId = "sku_fries_large",
                name = "Papas grandes",
                description = "Papas crocantes para acompañar el partido.",
                imageUrl = "https://placehold.co/640x480/f4b83f/1d2433?text=Papas",
                brand = "Turbo",
                categories = listOf("comida", "sports-night"),
                ageRestricted = false,
                referencePriceMinor = 499_000,
                currency = "ARS",
                status = ProductStatus.ACTIVE,
            ),
        )

    override fun findProducts(
        organizationId: UUID,
        filters: ProductFilters,
        limit: Int,
        cursor: String?,
    ): PageResult<CatalogProduct> {
        val query = filters.q?.trim()?.lowercase()
        val filtered =
            products.filter { product ->
                val matchesText =
                    query == null ||
                        product.name.lowercase().contains(query) ||
                        product.description?.lowercase()?.contains(query) == true
                val matchesCategory = filters.category == null || filters.category in product.categories
                val matchesStatus = filters.status == null || product.status == filters.status
                matchesText && matchesCategory && matchesStatus
            }
        return PageResult(items = filtered.take(limit), nextCursor = null)
    }
}
