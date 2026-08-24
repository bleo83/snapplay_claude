package io.snapplay.organization

import io.snapplay.organization.application.port.output.OrganizationRepository
import io.snapplay.organization.application.port.output.UpdateOrganizationInput
import io.snapplay.organization.domain.OrganizationStatus
import io.snapplay.organization.domain.OrganizationType
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

// Run with: ./gradlew test -Dtestcontainers.enabled=true
// Or from IntelliJ IDEA — Docker Desktop integrates automatically there.
// macOS + Docker Desktop requires the raw daemon socket which Testcontainers
// can't auto-discover from the terminal due to the Docker Desktop proxy layer.
@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers
@EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
class JdbcOrganizationRepositoryTest {
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
    lateinit var repository: OrganizationRepository

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private lateinit var orgId: UUID

    @BeforeEach
    fun setup() {
        jdbc.execute("DELETE FROM organizations")
        orgId = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO organizations (id, legal_name, display_name, organization_type,
                                       country, default_currency, timezone, status)
            VALUES (?, 'Disney S.A.', 'Disney Argentina', 'CONTENT_PROVIDER',
                    'AR', 'ARS', 'America/Argentina/Buenos_Aires', 'ACTIVE')
            """.trimIndent(),
            orgId,
        )
    }

    @Test
    fun `findById returns organization when it exists`() {
        val org = repository.findById(orgId)

        assertThat(org).isNotNull
        assertThat(org!!.id).isEqualTo(orgId)
        assertThat(org.legalName).isEqualTo("Disney S.A.")
        assertThat(org.displayName).isEqualTo("Disney Argentina")
        assertThat(org.organizationType).isEqualTo(OrganizationType.CONTENT_PROVIDER)
        assertThat(org.country).isEqualTo("AR")
        assertThat(org.defaultCurrency).isEqualTo("ARS")
        assertThat(org.status).isEqualTo(OrganizationStatus.ACTIVE)
    }

    @Test
    fun `findById returns null for unknown id`() {
        val org = repository.findById(UUID.randomUUID())
        assertThat(org).isNull()
    }

    @Test
    fun `update changes mutable fields and returns fresh state`() {
        val input =
            UpdateOrganizationInput(
                legalName = "Disney Updated S.A.",
                displayName = "Disney Updated",
                country = "MX",
                defaultCurrency = "MXN",
                timezone = "America/Mexico_City",
            )

        val updated = repository.update(orgId, input)

        assertThat(updated.legalName).isEqualTo("Disney Updated S.A.")
        assertThat(updated.displayName).isEqualTo("Disney Updated")
        assertThat(updated.country).isEqualTo("MX")
        assertThat(updated.defaultCurrency).isEqualTo("MXN")
        assertThat(updated.timezone).isEqualTo("America/Mexico_City")
        // Immutable fields unchanged
        assertThat(updated.organizationType).isEqualTo(OrganizationType.CONTENT_PROVIDER)
        assertThat(updated.status).isEqualTo(OrganizationStatus.ACTIVE)
    }
}
