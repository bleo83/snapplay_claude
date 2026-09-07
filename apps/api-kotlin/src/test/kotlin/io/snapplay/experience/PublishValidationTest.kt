package io.snapplay.experience

import io.snapplay.catalog.domain.RappiCatalogPage
import io.snapplay.catalog.domain.RappiCategorySnapshot
import io.snapplay.catalog.domain.RappiStoreSnapshot
import io.snapplay.catalog.infrastructure.client.DemoRappiCatalogClient
import io.snapplay.catalog.infrastructure.persistence.DemoCategoryRepository
import io.snapplay.catalog.infrastructure.persistence.DemoStoreRepository
import io.snapplay.experience.application.port.input.ValidateExperienceUseCase
import io.snapplay.identity.OrgRole
import io.snapplay.identity.RequestPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

private val ORG_ID = UUID.fromString("50d2d7eb-c8fd-42df-bbb0-0ea0e2271090")
private val CONNECTION_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")

// Demo experience: Hulu Classics (DRAFT) — store rappi-store-ar-001, category snacks-drinks
private val DRAFT_EXPERIENCE_ID = UUID.fromString("53d116c9-e676-4c49-9a2b-371d0b4d5c1a")

@SpringBootTest
@ActiveProfiles("demo")
class PublishValidationTest {
    @Autowired lateinit var validateUseCase: ValidateExperienceUseCase

    @Autowired lateinit var demoRappiClient: DemoRappiCatalogClient

    @Autowired lateinit var demoStoreRepo: DemoStoreRepository

    @Autowired lateinit var demoCategoryRepo: DemoCategoryRepository

    @Autowired lateinit var syncUseCase: io.snapplay.catalog.application.port.input.SyncCatalogUseCase

    private val principal =
        RequestPrincipal(
            userId = UUID.randomUUID(),
            organizationId = ORG_ID,
            roles = setOf(OrgRole.ORGANIZATION_ADMIN),
        )

    @BeforeEach
    fun setUp() {
        demoRappiClient.reset()
        demoStoreRepo.clear()
        demoCategoryRepo.clear()
    }

    @Test
    fun `validation passes when store and category are active and linked`() {
        seedCatalog("rappi-store-ar-001", "AR", listOf("snacks-drinks" to "Snacks & Drinks"))

        val result = validateUseCase.validate(DRAFT_EXPERIENCE_ID, principal)

        assertThat(result.valid).isTrue()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `store not found produces error`() {
        // No catalog synced — store 900000 does not exist
        val result = validateUseCase.validate(DRAFT_EXPERIENCE_ID, principal)

        assertThat(result.valid).isFalse()
        assertThat(result.errors).anyMatch { it.code == "STORE_NOT_FOUND" }
    }

    @Test
    fun `stale store produces warning`() {
        seedCatalog("rappi-store-ar-001", "AR", listOf("snacks-drinks" to "Snacks & Drinks"))

        // Second sync without the store → marks it STALE
        demoRappiClient.reset()
        demoRappiClient.enqueuePages(
            RappiCatalogPage(
                stores = listOf(RappiStoreSnapshot("other-store", "Other", "AR", listOf(RappiCategorySnapshot("snacks-drinks", "Snacks & Drinks")))),
                nextCursor = null,
            ),
        )
        syncUseCase.sync(CONNECTION_ID)

        val result = validateUseCase.validate(DRAFT_EXPERIENCE_ID, principal)

        assertThat(result.warnings).anyMatch { it.code == "STORE_STALE" }
    }

    @Test
    fun `category from another store produces CATEGORY_NOT_LINKED error`() {
        // rappi-store-ar-001 has cat-other; other-store has snacks-drinks
        // Experience references rappi-store-ar-001 + snacks-drinks → not linked
        demoRappiClient.enqueuePages(
            RappiCatalogPage(
                stores =
                    listOf(
                        RappiStoreSnapshot("rappi-store-ar-001", "Store A", "AR", listOf(RappiCategorySnapshot("cat-other", "Other Cat"))),
                        RappiStoreSnapshot("other-store", "Store B", "AR", listOf(RappiCategorySnapshot("snacks-drinks", "Snacks & Drinks"))),
                    ),
                nextCursor = null,
            ),
        )
        syncUseCase.sync(CONNECTION_ID)

        val result = validateUseCase.validate(DRAFT_EXPERIENCE_ID, principal)

        assertThat(result.valid).isFalse()
        assertThat(result.errors).anyMatch { it.code == "CATEGORY_NOT_LINKED" }
    }

    @Test
    fun `validate returns actionable field-level errors`() {
        // No catalog at all
        val result = validateUseCase.validate(DRAFT_EXPERIENCE_ID, principal)

        assertThat(result.errors).isNotEmpty
        // Every error has a field and message
        result.errors.forEach { issue ->
            assertThat(issue.field).isNotBlank()
            assertThat(issue.code).isNotBlank()
            assertThat(issue.message).isNotBlank()
        }
    }

    private fun seedCatalog(
        storeId: String,
        country: String,
        categories: List<Pair<String, String>>,
    ) {
        demoRappiClient.enqueuePages(
            RappiCatalogPage(
                stores =
                    listOf(
                        RappiStoreSnapshot(
                            providerStoreId = storeId,
                            name = "Test Store",
                            country = country,
                            categories = categories.map { (id, name) -> RappiCategorySnapshot(id, name) },
                        ),
                    ),
                nextCursor = null,
            ),
        )
        syncUseCase.sync(CONNECTION_ID)
    }
}
