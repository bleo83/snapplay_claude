package io.snapplay.analytics

import io.snapplay.links.domain.HandoffSession
import io.snapplay.links.domain.HandoffSessionStatus
import io.snapplay.links.infrastructure.persistence.DemoHandoffSessionRepository
import io.snapplay.partner.infrastructure.persistence.DemoProviderOrderRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class DashboardControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var demoSessionRepo: DemoHandoffSessionRepository

    @Autowired lateinit var demoOrderRepo: DemoProviderOrderRepository

    @BeforeEach
    fun setUp() {
        demoSessionRepo.clear()
        demoOrderRepo.clear()
    }

    @Test
    fun `GET dashboard returns metrics and funnel steps`() {
        val now = Instant.now()
        demoSessionRepo.create(
            HandoffSession(
                id = UUID.randomUUID(),
                smartLinkId = UUID.randomUUID(),
                experienceVersionId = UUID.randomUUID(),
                connectionId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                trackingTokenHash = "abc123",
                dataSharingMode = "FULL",
                status = HandoffSessionStatus.REDIRECTED,
                expiresAt = now.plus(30, ChronoUnit.MINUTES),
                createdAt = now,
                updatedAt = now,
            ),
        )

        mockMvc.get("/v1/dashboard")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.metrics.scans") { value(1) } }
            .andExpect { jsonPath("$.metrics.handoffs") { value(1) } }
            .andExpect { jsonPath("$.rangeLabel") { isString() } }
            .andExpect { jsonPath("$.currency") { isString() } }
            .andExpect { jsonPath("$.freshness") { isString() } }
            .andExpect { jsonPath("$.funnelSteps") { isArray() } }
            .andExpect { jsonPath("$.funnelSteps.length()") { value(4) } }
    }

    @Test
    fun `GET dashboard with period filter respects date range`() {
        mockMvc.get("/v1/dashboard?from=2020-01-01T00:00:00Z&to=2020-01-02T00:00:00Z")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.metrics.scans") { value(0) } }
    }

    @Test
    fun `dashboard does not expose PII`() {
        mockMvc.get("/v1/dashboard")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.metrics") { isMap() } }
            // No user identifiers in the response
            .andExpect { jsonPath("$.userId") { doesNotExist() } }
            .andExpect { jsonPath("$.email") { doesNotExist() } }
            .andExpect { jsonPath("$.trackingToken") { doesNotExist() } }
    }
}
