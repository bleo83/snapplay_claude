package io.snapplay.catalog

import io.snapplay.catalog.application.port.output.CatalogRepository
import io.snapplay.catalog.application.port.output.ProductFilters
import io.snapplay.catalog.domain.ProductStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers
@EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
class JdbcCatalogRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var repository: CatalogRepository

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private lateinit var orgId: UUID
    private lateinit var connectionId: UUID

    @BeforeEach
    fun setup() {
        jdbc.execute("DELETE FROM catalog_products")
        jdbc.execute("DELETE FROM connections")
        jdbc.execute("DELETE FROM data_sharing_policies")
        jdbc.execute("DELETE FROM organizations")

        orgId = UUID.randomUUID()
        val commerceOrgId = UUID.randomUUID()
        val policyId = UUID.randomUUID()
        connectionId = UUID.randomUUID()

        jdbc.update(
            "INSERT INTO organizations (id, legal_name, display_name, organization_type, country, default_currency, timezone) VALUES (?, 'Disney', 'Disney', 'CONTENT_PROVIDER', 'AR', 'ARS', 'America/Argentina/Buenos_Aires')",
            orgId,
        )
        jdbc.update(
            "INSERT INTO organizations (id, legal_name, display_name, organization_type, country, default_currency, timezone) VALUES (?, 'Rappi', 'Rappi', 'COMMERCE_PROVIDER', 'AR', 'ARS', 'America/Argentina/Buenos_Aires')",
            commerceOrgId,
        )
        jdbc.update(
            "INSERT INTO data_sharing_policies (id, owner_organization_id, name, mode) VALUES (?, ?, 'default', 'BILLING_ONLY')",
            policyId,
            orgId,
        )
        jdbc.update(
            "INSERT INTO connections (id, name, content_organization_id, commerce_organization_id, connector_key, environment, territories, capabilities, data_sharing_policy_id) VALUES (?, 'Disney x Rappi', ?, ?, 'rappi', 'SANDBOX', '{AR}', '{}', ?)",
            connectionId,
            orgId,
            commerceOrgId,
            policyId,
        )

        // Insert 3 test products
        insertProduct("sku_cup", "Vaso Disney", listOf("merchandising", "movie-night"), false, 1_000_00L, "ACTIVE")
        insertProduct("sku_pop", "Pochoclos", listOf("snacks"), false, 500_00L, "ACTIVE")
        insertProduct("sku_beer", "Cerveza", listOf("bebidas", "adultos"), true, 300_00L, "INACTIVE")
    }

    private fun insertProduct(
        sku: String,
        name: String,
        categories: List<String>,
        ageRestricted: Boolean,
        priceMinor: Long,
        status: String,
    ) {
        jdbc.update(
            """
            INSERT INTO catalog_products
                (connection_id, provider_product_id, name, categories, age_restricted, reference_price_minor, currency, status)
            VALUES (?, ?, ?, ?, ?, ?, 'ARS', ?)
            """.trimIndent(),
            connectionId,
            sku,
            name,
            jdbc.dataSource!!.connection.createArrayOf("text", categories.toTypedArray()),
            ageRestricted,
            priceMinor,
            status,
        )
    }

    @Test
    fun `findProducts returns all products for org with no filters`() {
        val result = repository.findProducts(orgId, ProductFilters(), 50, null)
        assertThat(result.items).hasSize(3)
        assertThat(result.nextCursor).isNull()
    }

    @Test
    fun `findProducts filters by status ACTIVE`() {
        val result = repository.findProducts(orgId, ProductFilters(status = ProductStatus.ACTIVE), 50, null)
        assertThat(result.items).hasSize(2)
        assertThat(result.items).allMatch { it.status == ProductStatus.ACTIVE }
    }

    @Test
    fun `findProducts filters by category`() {
        val result = repository.findProducts(orgId, ProductFilters(category = "movie-night"), 50, null)
        assertThat(result.items).hasSize(1)
        assertThat(result.items.first().providerProductId).isEqualTo("sku_cup")
    }

    @Test
    fun `findProducts filters by text query on name`() {
        val result = repository.findProducts(orgId, ProductFilters(q = "vaso"), 50, null)
        assertThat(result.items).hasSize(1)
        assertThat(result.items.first().name).isEqualTo("Vaso Disney")
    }

    @Test
    fun `findProducts returns empty for unknown org`() {
        val result = repository.findProducts(UUID.randomUUID(), ProductFilters(), 50, null)
        assertThat(result.items).isEmpty()
    }

    @Test
    fun `findProducts respects limit and returns nextCursor when more results exist`() {
        val result = repository.findProducts(orgId, ProductFilters(), 2, null)
        assertThat(result.items).hasSize(2)
        assertThat(result.nextCursor).isNotNull()
    }

    @Test
    fun `findProducts cursor fetches next page`() {
        val page1 = repository.findProducts(orgId, ProductFilters(), 2, null)
        assertThat(page1.items).hasSize(2)

        val page2 = repository.findProducts(orgId, ProductFilters(), 2, page1.nextCursor)
        assertThat(page2.items).hasSize(1)
        assertThat(page2.nextCursor).isNull()

        val allIds = (page1.items + page2.items).map { it.id }
        assertThat(allIds).doesNotHaveDuplicates()
    }
}
