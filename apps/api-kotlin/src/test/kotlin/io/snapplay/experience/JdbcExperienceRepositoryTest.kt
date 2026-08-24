package io.snapplay.experience

import io.snapplay.experience.application.port.output.CreateExperienceInput
import io.snapplay.experience.application.port.output.ExperienceRepository
import io.snapplay.experience.domain.ExperienceStatus
import io.snapplay.experience.domain.HandoffMode
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
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers
@EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
class JdbcExperienceRepositoryTest {
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
    lateinit var repository: ExperienceRepository

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private lateinit var orgId: UUID
    private lateinit var commerceOrgId: UUID
    private lateinit var channelId: UUID
    private lateinit var contextId: UUID
    private lateinit var connectionId: UUID
    private lateinit var contractId: UUID

    @BeforeEach
    fun setup() {
        listOf(
            "DELETE FROM experience_versions",
            "DELETE FROM experiences",
            "DELETE FROM catalog_products",
            "DELETE FROM commercial_contracts",
            "DELETE FROM contract_versions",
            "DELETE FROM connections",
            "DELETE FROM content_contexts",
            "DELETE FROM channels",
            "DELETE FROM data_sharing_policies",
            "DELETE FROM organizations",
        ).forEach { jdbc.execute(it) }

        orgId = UUID.randomUUID()
        commerceOrgId = UUID.randomUUID()
        channelId = UUID.randomUUID()
        contextId = UUID.randomUUID()
        val policyId = UUID.randomUUID()
        connectionId = UUID.randomUUID()
        contractId = UUID.randomUUID()

        jdbc.update(
            "INSERT INTO organizations (id, legal_name, display_name, organization_type, country, default_currency, timezone) VALUES (?, 'Disney', 'Disney', 'CONTENT_PROVIDER', 'AR', 'ARS', 'America/Argentina/Buenos_Aires')",
            orgId,
        )
        jdbc.update(
            "INSERT INTO organizations (id, legal_name, display_name, organization_type, country, default_currency, timezone) VALUES (?, 'Rappi', 'Rappi', 'COMMERCE_PROVIDER', 'AR', 'ARS', 'America/Argentina/Buenos_Aires')",
            commerceOrgId,
        )
        jdbc.update(
            "INSERT INTO channels (id, organization_id, channel_key, display_name) VALUES (?, ?, 'disney_plus', 'Disney+')",
            channelId,
            orgId,
        )
        jdbc.update(
            "INSERT INTO content_contexts (id, organization_id, channel_id, external_ref, context_type, title) VALUES (?, ?, ?, 'toy_story', 'MOVIE', 'Toy Story')",
            contextId,
            orgId,
            channelId,
        )
        jdbc.update(
            "INSERT INTO data_sharing_policies (id, owner_organization_id, name, mode) VALUES (?, ?, 'default', 'BILLING_ONLY')",
            policyId,
            orgId,
        )
        jdbc.update(
            "INSERT INTO connections (id, name, content_organization_id, commerce_organization_id, connector_key, environment, territories, capabilities, data_sharing_policy_id, status) VALUES (?, 'Disney x Rappi', ?, ?, 'rappi', 'SANDBOX', '{AR}', '{}', ?, 'ACTIVE')",
            connectionId,
            orgId,
            commerceOrgId,
            policyId,
        )
        jdbc.update(
            "INSERT INTO commercial_contracts (id, name, publisher_organization_id, commerce_organization_id, territories, status) VALUES (?, 'Pilot', ?, ?, '{AR}', 'ACTIVE')",
            contractId,
            orgId,
            commerceOrgId,
        )
    }

    // --- findContentContext ---

    @Test
    fun `findContentContext returns result for known context title`() {
        val result = repository.findContentContext(orgId, "Toy Story")
        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo(contextId)
        assertThat(result.channelDisplayName).isEqualTo("Disney+")
    }

    @Test
    fun `findContentContext returns null for unknown context title`() {
        assertThat(repository.findContentContext(orgId, "Unknown Movie")).isNull()
    }

    // --- findActiveConnectionId ---

    @Test
    fun `findActiveConnectionId returns connection id when active`() {
        assertThat(repository.findActiveConnectionId(orgId)).isEqualTo(connectionId)
    }

    @Test
    fun `findActiveConnectionId returns null when no active connection`() {
        jdbc.update("UPDATE connections SET status = 'INACTIVE' WHERE id = ?", connectionId)
        assertThat(repository.findActiveConnectionId(orgId)).isNull()
    }

    // --- findActiveContractId ---

    @Test
    fun `findActiveContractId returns contract id when active`() {
        assertThat(repository.findActiveContractId(orgId)).isEqualTo(contractId)
    }

    @Test
    fun `findActiveContractId returns null when no active contract`() {
        jdbc.update("UPDATE commercial_contracts SET status = 'EXPIRED' WHERE id = ?", contractId)
        assertThat(repository.findActiveContractId(orgId)).isNull()
    }

    // --- findAll / create ---

    @Test
    fun `findAll returns empty list when no experiences exist`() {
        assertThat(repository.findAll(orgId)).isEmpty()
    }

    @Test
    fun `create inserts experience and version, returns domain object`() {
        val input = buildInput()

        val created = repository.create(orgId, UUID.randomUUID(), input)

        assertThat(created.name).isEqualTo("Toy Story Night")
        assertThat(created.status).isEqualTo(ExperienceStatus.DRAFT)
        assertThat(created.version).isEqualTo(1)
        assertThat(created.handoffMode).isEqualTo(HandoffMode.STORE_DEEPLINK)
        assertThat(created.contextTitle).isEqualTo("Toy Story")
        assertThat(created.channel).isEqualTo("Disney+")
        assertThat(created.productCount).isEqualTo(0)
    }

    @Test
    fun `findAll returns created experience with correct joins`() {
        repository.create(orgId, UUID.randomUUID(), buildInput())

        val experiences = repository.findAll(orgId)
        assertThat(experiences).hasSize(1)
        assertThat(experiences.first().contextTitle).isEqualTo("Toy Story")
        assertThat(experiences.first().channel).isEqualTo("Disney+")
    }

    private fun buildInput() =
        CreateExperienceInput(
            name = "Toy Story Night",
            contextId = contextId,
            contextTitle = "Toy Story",
            channelDisplayName = "Disney+",
            connectionId = connectionId,
            contractId = contractId,
            productIds = emptyList(),
            handoffMode = HandoffMode.STORE_DEEPLINK,
            startsAt = Instant.parse("2026-09-01T00:00:00Z"),
            endsAt = null,
        )
}
