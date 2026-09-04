package io.snapplay.catalog

import io.snapplay.catalog.application.port.input.SyncCatalogUseCase
import io.snapplay.catalog.domain.CatalogEntityStatus
import io.snapplay.catalog.domain.RappiCatalogPage
import io.snapplay.catalog.domain.RappiCategorySnapshot
import io.snapplay.catalog.domain.RappiStoreSnapshot
import io.snapplay.catalog.infrastructure.client.DemoRappiCatalogClient
import io.snapplay.catalog.infrastructure.persistence.DemoCategoryRepository
import io.snapplay.catalog.infrastructure.persistence.DemoStoreRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.UUID

private val CONNECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001")

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class CatalogSyncTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var syncUseCase: SyncCatalogUseCase

    @Autowired lateinit var demoRappiClient: DemoRappiCatalogClient

    @Autowired lateinit var demoStoreRepo: DemoStoreRepository

    @Autowired lateinit var demoCategoryRepo: DemoCategoryRepository

    @BeforeEach
    fun setUp() {
        demoRappiClient.reset()
        demoStoreRepo.clear()
        demoCategoryRepo.clear()
    }

    @Test
    fun `sync imports stores and categories with relationships`() {
        demoRappiClient.enqueuePages(
            RappiCatalogPage(
                stores =
                    listOf(
                        RappiStoreSnapshot(
                            providerStoreId = "store-001",
                            name = "Rappi Store Buenos Aires",
                            country = "AR",
                            categories =
                                listOf(
                                    RappiCategorySnapshot("cat-001", "Bebidas"),
                                    RappiCategorySnapshot("cat-002", "Snacks"),
                                ),
                        ),
                        RappiStoreSnapshot(
                            providerStoreId = "store-002",
                            name = "Rappi Store Bogotá",
                            country = "CO",
                            categories =
                                listOf(
                                    RappiCategorySnapshot("cat-001", "Bebidas"),
                                ),
                        ),
                    ),
                nextCursor = null,
            ),
        )

        val result = syncUseCase.sync(CONNECTION_ID)

        assertThat(result.storesUpserted).isEqualTo(2)
        assertThat(result.categoriesUpserted).isEqualTo(3)
        assertThat(result.relationsCreated).isEqualTo(3)
        assertThat(demoStoreRepo.all()).hasSize(2)
        // cat-001 "Bebidas" appears in both stores but is deduplicated by providerCategoryId
        assertThat(demoCategoryRepo.all()).hasSize(2)
    }

    @Test
    fun `reprocessing same page does not duplicate`() {
        val page =
            RappiCatalogPage(
                stores =
                    listOf(
                        RappiStoreSnapshot(
                            "store-001",
                            "Store A",
                            "AR",
                            listOf(RappiCategorySnapshot("cat-001", "Bebidas")),
                        ),
                    ),
                nextCursor = null,
            )

        demoRappiClient.enqueuePages(page)
        syncUseCase.sync(CONNECTION_ID)

        demoRappiClient.reset()
        demoRappiClient.enqueuePages(page)
        syncUseCase.sync(CONNECTION_ID)

        assertThat(demoStoreRepo.all()).hasSize(1)
        assertThat(demoCategoryRepo.all()).hasSize(1)
    }

    @Test
    fun `sync updates name on existing store`() {
        demoRappiClient.enqueuePages(
            RappiCatalogPage(
                stores = listOf(RappiStoreSnapshot("store-001", "Old Name", "AR", emptyList())),
                nextCursor = null,
            ),
        )
        syncUseCase.sync(CONNECTION_ID)

        assertThat(demoStoreRepo.all().first().name).isEqualTo("Old Name")

        demoRappiClient.reset()
        demoRappiClient.enqueuePages(
            RappiCatalogPage(
                stores = listOf(RappiStoreSnapshot("store-001", "New Name", "AR", emptyList())),
                nextCursor = null,
            ),
        )
        syncUseCase.sync(CONNECTION_ID)

        assertThat(demoStoreRepo.all()).hasSize(1)
        assertThat(demoStoreRepo.all().first().name).isEqualTo("New Name")
    }

    @Test
    fun `stale stores and categories are marked after sync`() {
        // First sync: import store-001 and store-002
        demoRappiClient.enqueuePages(
            RappiCatalogPage(
                stores =
                    listOf(
                        RappiStoreSnapshot("store-001", "Store A", "AR", listOf(RappiCategorySnapshot("cat-001", "Cat A"))),
                        RappiStoreSnapshot("store-002", "Store B", "AR", listOf(RappiCategorySnapshot("cat-002", "Cat B"))),
                    ),
                nextCursor = null,
            ),
        )
        syncUseCase.sync(CONNECTION_ID)
        assertThat(demoStoreRepo.all().filter { it.status == CatalogEntityStatus.ACTIVE }).hasSize(2)

        // Second sync: only store-001 appears — store-002 should become STALE
        demoRappiClient.reset()
        demoRappiClient.enqueuePages(
            RappiCatalogPage(
                stores =
                    listOf(
                        RappiStoreSnapshot("store-001", "Store A", "AR", listOf(RappiCategorySnapshot("cat-001", "Cat A"))),
                    ),
                nextCursor = null,
            ),
        )
        val result = syncUseCase.sync(CONNECTION_ID)

        assertThat(result.storesDeactivated).isEqualTo(1)
        assertThat(result.categoriesDeactivated).isEqualTo(1)

        val staleStores = demoStoreRepo.all().filter { it.status == CatalogEntityStatus.STALE }
        assertThat(staleStores).hasSize(1)
        assertThat(staleStores.first().providerStoreId).isEqualTo("store-002")
    }

    @Test
    fun `category not returned for unrelated store`() {
        demoRappiClient.enqueuePages(
            RappiCatalogPage(
                stores =
                    listOf(
                        RappiStoreSnapshot("store-001", "Store A", "AR", listOf(RappiCategorySnapshot("cat-001", "Bebidas"))),
                        RappiStoreSnapshot("store-002", "Store B", "AR", listOf(RappiCategorySnapshot("cat-002", "Snacks"))),
                    ),
                nextCursor = null,
            ),
        )
        syncUseCase.sync(CONNECTION_ID)

        val storeA = demoStoreRepo.all().first { it.providerStoreId == "store-001" }
        val storeB = demoStoreRepo.all().first { it.providerStoreId == "store-002" }

        // Store A only has Bebidas
        mockMvc.get("/v1/catalog/stores/${storeA.id}/categories")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.items.length()") { value(1) } }
            .andExpect { jsonPath("$.items[0].name") { value("Bebidas") } }

        // Store B only has Snacks
        mockMvc.get("/v1/catalog/stores/${storeB.id}/categories")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.items.length()") { value(1) } }
            .andExpect { jsonPath("$.items[0].name") { value("Snacks") } }
    }

    @Test
    fun `list stores API returns stores for connection`() {
        demoRappiClient.enqueuePages(
            RappiCatalogPage(
                stores =
                    listOf(
                        RappiStoreSnapshot("store-001", "Store A", "AR", emptyList()),
                        RappiStoreSnapshot("store-002", "Store B", "CO", emptyList()),
                    ),
                nextCursor = null,
            ),
        )
        syncUseCase.sync(CONNECTION_ID)

        mockMvc.get("/v1/catalog/stores?connectionId=$CONNECTION_ID")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.items.length()") { value(2) } }
    }

    @Test
    fun `list stores with status filter`() {
        demoRappiClient.enqueuePages(
            RappiCatalogPage(
                stores =
                    listOf(
                        RappiStoreSnapshot("store-001", "Active Store", "AR", emptyList()),
                        RappiStoreSnapshot("store-002", "Soon Stale", "AR", emptyList()),
                    ),
                nextCursor = null,
            ),
        )
        syncUseCase.sync(CONNECTION_ID)

        // Second sync drops store-002
        demoRappiClient.reset()
        demoRappiClient.enqueuePages(
            RappiCatalogPage(
                stores = listOf(RappiStoreSnapshot("store-001", "Active Store", "AR", emptyList())),
                nextCursor = null,
            ),
        )
        syncUseCase.sync(CONNECTION_ID)

        mockMvc.get("/v1/catalog/stores?connectionId=$CONNECTION_ID&status=ACTIVE")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.items.length()") { value(1) } }
            .andExpect { jsonPath("$.items[0].providerStoreId") { value("store-001") } }

        mockMvc.get("/v1/catalog/stores?connectionId=$CONNECTION_ID&status=STALE")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.items.length()") { value(1) } }
            .andExpect { jsonPath("$.items[0].providerStoreId") { value("store-002") } }
    }
}
