package io.snapplay.links

import io.snapplay.links.application.port.output.SmartLinkRepository
import io.snapplay.links.domain.SmartLinkStatus
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
class JdbcSmartLinkRepositoryTest {
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
    lateinit var repository: SmartLinkRepository

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private lateinit var orgId: UUID
    private lateinit var experienceId: UUID

    @BeforeEach
    fun setup() {
        listOf(
            "DELETE FROM handoff_sessions",
            "DELETE FROM smart_links",
            "DELETE FROM experience_versions",
            "DELETE FROM experiences",
            "DELETE FROM content_contexts",
            "DELETE FROM channels",
            "DELETE FROM organizations",
        ).forEach { jdbc.execute(it) }

        orgId = UUID.randomUUID()
        experienceId = UUID.randomUUID()
        val channelId = UUID.randomUUID()
        val contextId = UUID.randomUUID()

        jdbc.update(
            "INSERT INTO organizations (id, legal_name, display_name, organization_type, country, default_currency, timezone) VALUES (?, 'Disney', 'Disney', 'CONTENT_PROVIDER', 'AR', 'ARS', 'America/Argentina/Buenos_Aires')",
            orgId,
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
            "INSERT INTO experiences (id, organization_id, content_context_id, name, status, handoff_mode) VALUES (?, ?, ?, 'Toy Story Night', 'PUBLISHED', 'STORE_DEEPLINK')",
            experienceId,
            orgId,
            contextId,
        )
    }

    @Test
    fun `findAll returns empty list when no smart links exist`() {
        assertThat(repository.findAll(orgId)).isEmpty()
    }

    @Test
    fun `findAll returns smart link with correct fields`() {
        val smartLinkId = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO smart_links (id, organization_id, experience_id, short_code, placement_key, status) VALUES (?, ?, ?, 'ABC123', 'disney-plus.toy-story.endcard', 'ACTIVE')",
            smartLinkId,
            orgId,
            experienceId,
        )

        val links = repository.findAll(orgId)

        assertThat(links).hasSize(1)
        assertThat(links.first().id).isEqualTo(smartLinkId)
        assertThat(links.first().shortCode).isEqualTo("ABC123")
        assertThat(links.first().experienceName).isEqualTo("Toy Story Night")
        assertThat(links.first().placementKey).isEqualTo("disney-plus.toy-story.endcard")
        assertThat(links.first().status).isEqualTo(SmartLinkStatus.ACTIVE)
        assertThat(links.first().scans).isEqualTo(0)
        assertThat(links.first().conversions).isEqualTo(0)
    }

    @Test
    fun `findAll counts scans and conversions correctly`() {
        val smartLinkId = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO smart_links (id, organization_id, experience_id, short_code, placement_key, status) VALUES (?, ?, ?, 'XYZ789', 'disney-plus.toy-story.pause', 'ACTIVE')",
            smartLinkId,
            orgId,
            experienceId,
        )
        // 3 scans, 1 converted
        repeat(3) {
            val sessionId = UUID.randomUUID()
            val status = if (it == 0) "CONVERTED" else "INITIATED"
            jdbc.update(
                "INSERT INTO handoff_sessions (id, smart_link_id, status) VALUES (?, ?, ?)",
                sessionId,
                smartLinkId,
                status,
            )
        }

        val links = repository.findAll(orgId)

        assertThat(links.first().scans).isEqualTo(3)
        assertThat(links.first().conversions).isEqualTo(1)
    }

    @Test
    fun `findAll does not return smart links from other organizations`() {
        val otherOrgId = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO organizations (id, legal_name, display_name, organization_type, country, default_currency, timezone) VALUES (?, 'Rappi', 'Rappi', 'COMMERCE_PROVIDER', 'AR', 'ARS', 'America/Argentina/Buenos_Aires')",
            otherOrgId,
        )
        jdbc.update(
            "INSERT INTO smart_links (id, organization_id, experience_id, short_code, placement_key, status) VALUES (?, ?, ?, 'OTHER1', 'some.placement', 'ACTIVE')",
            UUID.randomUUID(),
            otherOrgId,
            experienceId,
        )

        assertThat(repository.findAll(orgId)).isEmpty()
    }
}
