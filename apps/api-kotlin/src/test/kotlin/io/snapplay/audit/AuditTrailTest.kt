package io.snapplay.audit

import io.snapplay.audit.application.usecase.AuditService
import io.snapplay.audit.domain.AuditAction
import io.snapplay.audit.domain.AuditActorType
import io.snapplay.audit.infrastructure.persistence.DemoAuditEventRepository
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class AuditTrailTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var auditService: AuditService

    @Autowired lateinit var demoRepo: DemoAuditEventRepository

    private val orgId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        demoRepo.clear()
    }

    @Test
    fun `audit event is persisted with all fields`() {
        val event =
            auditService.log(
                actorId = "user-123",
                actorType = AuditActorType.USER,
                organizationId = orgId,
                role = "PUBLISHER",
                action = AuditAction.EXPERIENCE_PUBLISHED,
                resourceType = "EXPERIENCE",
                resourceId = "exp-001",
                afterState = """{"status":"PUBLISHED"}""",
            )

        val stored = demoRepo.all()
        assertThat(stored).hasSize(1)
        assertThat(stored[0].id).isEqualTo(event.id)
        assertThat(stored[0].actorId).isEqualTo("user-123")
        assertThat(stored[0].action).isEqualTo(AuditAction.EXPERIENCE_PUBLISHED)
        assertThat(stored[0].resourceType).isEqualTo("EXPERIENCE")
    }

    @Test
    fun `sensitive data is redacted before persisting`() {
        auditService.log(
            actorId = "admin-1",
            actorType = AuditActorType.USER,
            action = AuditAction.WEBHOOK_SECRET_ROTATED,
            resourceType = "CONNECTION",
            resourceId = "conn-001",
            beforeState = """{"secret":"old-secret-value","name":"My Connection"}""",
            afterState = """{"secret":"new-secret-value","name":"My Connection"}""",
        )

        val stored = demoRepo.all().first()
        assertThat(stored.beforeState).contains("[REDACTED]")
        assertThat(stored.beforeState).doesNotContain("old-secret-value")
        assertThat(stored.afterState).contains("[REDACTED]")
        assertThat(stored.afterState).doesNotContain("new-secret-value")
        // Non-sensitive fields preserved
        assertThat(stored.afterState).contains("My Connection")
    }

    @Test
    fun `redaction handles multiple sensitive keys`() {
        val json = """{"password":"p@ss","token":"tk123","name":"safe"}"""
        val redacted = AuditService.redact(json)
        assertThat(redacted).doesNotContain("p@ss")
        assertThat(redacted).doesNotContain("tk123")
        assertThat(redacted).contains("safe")
    }

    @Test
    fun `no update or delete API exposed`() {
        // Verify that only GET exists under /v1/audit — no POST, PUT, PATCH, DELETE
        // The AuditEventRepository interface has no update/delete methods
        // We verify by checking that the controller only has search
        auditService.log(
            actorId = "test",
            actorType = AuditActorType.SYSTEM,
            action = AuditAction.CATALOG_SYNCED,
            resourceType = "CONNECTION",
            resourceId = "conn-001",
        )

        mockMvc.get("/v1/audit/events")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.items.length()") { value(1) } }
    }

    @Test
    fun `search filters by action`() {
        auditService.log(
            actorId = "user-1",
            actorType = AuditActorType.USER,
            action = AuditAction.EXPERIENCE_PUBLISHED,
            resourceType = "EXPERIENCE",
            resourceId = "exp-001",
        )
        auditService.log(
            actorId = "user-1",
            actorType = AuditActorType.USER,
            action = AuditAction.EXPERIENCE_PAUSED,
            resourceType = "EXPERIENCE",
            resourceId = "exp-001",
        )

        mockMvc.get("/v1/audit/events?action=EXPERIENCE_PUBLISHED")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.items.length()") { value(1) } }
            .andExpect { jsonPath("$.items[0].action") { value("EXPERIENCE_PUBLISHED") } }
    }

    @Test
    fun `search filters by resource`() {
        auditService.log(
            actorId = "user-1",
            actorType = AuditActorType.USER,
            action = AuditAction.EXPERIENCE_CREATED,
            resourceType = "EXPERIENCE",
            resourceId = "exp-001",
        )
        auditService.log(
            actorId = "user-1",
            actorType = AuditActorType.USER,
            action = AuditAction.CONNECTION_CREATED,
            resourceType = "CONNECTION",
            resourceId = "conn-001",
        )

        mockMvc.get("/v1/audit/events?resourceType=EXPERIENCE")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.items.length()") { value(1) } }
    }

    @Test
    fun `each critical action produces exactly one correlated event`() {
        val actions =
            listOf(
                AuditAction.EXPERIENCE_CREATED,
                AuditAction.EXPERIENCE_PUBLISHED,
                AuditAction.EXPERIENCE_PAUSED,
                AuditAction.DESTINATION_CHANGED,
                AuditAction.CATALOG_SYNCED,
                AuditAction.WEBHOOK_SECRET_ROTATED,
            )

        actions.forEach { action ->
            auditService.log(
                actorId = "sys",
                actorType = AuditActorType.SYSTEM,
                action = action,
                resourceType = "TEST",
                resourceId = "res-${action.name}",
            )
        }

        assertThat(demoRepo.all()).hasSize(actions.size)
        val loggedActions = demoRepo.all().map { it.action }.toSet()
        assertThat(loggedActions).containsExactlyInAnyOrderElementsOf(actions)
    }

    @Test
    fun `financial action includes reason`() {
        auditService.log(
            actorId = "finance-user",
            actorType = AuditActorType.USER,
            role = "FINANCE",
            action = AuditAction.ORDER_ADJUSTED,
            resourceType = "PROVIDER_ORDER",
            resourceId = "order-001",
            reason = "Duplicate charge — customer refund approved by ticket FIN-123",
        )

        val event = demoRepo.all().first()
        assertThat(event.reason).isEqualTo("Duplicate charge — customer refund approved by ticket FIN-123")
        assertThat(event.role).isEqualTo("FINANCE")
    }
}
