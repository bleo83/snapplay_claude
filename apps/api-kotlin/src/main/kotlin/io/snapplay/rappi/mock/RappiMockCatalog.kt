package io.snapplay.rappi.mock

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * In-memory mock catalog of Rappi stores and categories.
 *
 * Seed data matches the demo experience providerStoreId / providerCategoryId values
 * so the full E2E flow works: publication → QR scan → landing page → order event.
 *
 * Excluded from production — active only when snapplay.rappi-mock-enabled=true.
 */
@Component
@ConditionalOnProperty(name = ["snapplay.rappi-mock-enabled"], havingValue = "true")
class RappiMockCatalog {
    data class MockCategory(val id: String, val name: String)

    data class MockStore(val id: String, val name: String, val categories: List<MockCategory>)

    private val stores: Map<String, MockStore> =
        mapOf(
            "900000" to
                MockStore(
                    id = "900000",
                    name = "Toy Story Movie Night",
                    categories =
                        listOf(
                            MockCategory("2000", "Snacks & Drinks"),
                            MockCategory("2001", "Candy & Sweets"),
                        ),
                ),
            "900001" to
                MockStore(
                    id = "900001",
                    name = "Moana Family Night",
                    categories =
                        listOf(
                            MockCategory("3000", "Popcorn & Snacks"),
                            MockCategory("3001", "Tropical Drinks"),
                        ),
                ),
            "900002" to
                MockStore(
                    id = "900002",
                    name = "Hulu Hub",
                    categories =
                        listOf(
                            MockCategory("4000", "Streaming Snacks"),
                        ),
                ),
            "900003" to
                MockStore(
                    id = "900003",
                    name = "ESPN Sports Zone",
                    categories =
                        listOf(
                            MockCategory("5000", "Sports Snacks"),
                            MockCategory("5001", "Energy Drinks"),
                        ),
                ),
        )

    fun findStore(storeId: String): MockStore? = stores[storeId]

    fun findCategory(
        storeId: String,
        categoryId: String,
    ): MockCategory? = stores[storeId]?.categories?.firstOrNull { it.id == categoryId }
}
