package io.snapplay.analytics

import io.snapplay.links.domain.HandoffSession
import io.snapplay.links.domain.HandoffSessionStatus
import io.snapplay.links.infrastructure.persistence.DemoHandoffSessionRepository
import io.snapplay.partner.application.port.output.ProviderOrderUpsert
import io.snapplay.partner.domain.ProviderOrderStatus
import io.snapplay.partner.infrastructure.persistence.DemoProviderOrderRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private val CONNECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000099")
private val OTHER_CONNECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000098")

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class AttributionFunnelControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var sessionRepo: DemoHandoffSessionRepository

    @Autowired
    lateinit var orderRepo: DemoProviderOrderRepository

    private val now = Instant.now()
    private val from = now.minus(1, ChronoUnit.HOURS).toString()
    private val to = now.plus(1, ChronoUnit.HOURS).toString()

    @BeforeEach
    fun seed() {
        sessionRepo.clear()
        orderRepo.clear()
        // Session 1: CONVERTED (has order)
        val sessionId1 = UUID.randomUUID()
        sessionRepo.create(
            session(
                id = sessionId1,
                connectionId = CONNECTION_ID,
                status = HandoffSessionStatus.CONVERTED,
                dataSharingMode = "AGGREGATED",
            ),
        )
        orderRepo.upsert(
            ProviderOrderUpsert(
                connectionId = CONNECTION_ID,
                handoffId = sessionId1,
                providerOrderRef = "ORD-001",
                newStatus = ProviderOrderStatus.DELIVERED,
                currency = "ARS",
                orderTotalMinor = 1599L,
                placedAt = now,
                deliveredAt = now,
            ),
            UUID.randomUUID(),
        )

        // Session 2: REDIRECTED (no order)
        sessionRepo.create(
            session(
                id = UUID.randomUUID(),
                connectionId = CONNECTION_ID,
                status = HandoffSessionStatus.REDIRECTED,
                dataSharingMode = "AGGREGATED",
            ),
        )

        // Session 3: CREATED (no redirect)
        sessionRepo.create(
            session(
                id = UUID.randomUUID(),
                connectionId = CONNECTION_ID,
                status = HandoffSessionStatus.CREATED,
                dataSharingMode = "AGGREGATED",
            ),
        )

        // Session 4: different connection — should not appear in filtered queries
        sessionRepo.create(
            session(
                id = UUID.randomUUID(),
                connectionId = OTHER_CONNECTION_ID,
                status = HandoffSessionStatus.CONVERTED,
                dataSharingMode = "BILLING_ONLY",
            ),
        )
    }

    @Test
    fun `funnel counts are correct for a specific connection`() {
        mockMvc
            .get("/v1/analytics/attribution/funnel") {
                param("connectionId", CONNECTION_ID.toString())
                param("from", from)
                param("to", to)
            }
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.scans") { value(3) }
                jsonPath("$.redirects") { value(2) }
                jsonPath("$.converted") { value(1) }
                jsonPath("$.ordersPlaced") { value(1) }
                jsonPath("$.ordersDelivered") { value(1) }
            }
    }

    @Test
    fun `gmvMinor is null for AGGREGATED connection`() {
        mockMvc
            .get("/v1/analytics/attribution/funnel") {
                param("connectionId", CONNECTION_ID.toString())
                param("from", from)
                param("to", to)
            }
            .andExpect {
                status { isOk() }
                // AGGREGATED connection — GMV suppressed
                jsonPath("$.gmvMinor") { doesNotExist() }
            }
    }

    @Test
    fun `unfiltered funnel aggregates all connections`() {
        mockMvc
            .get("/v1/analytics/attribution/funnel") {
                param("from", from)
                param("to", to)
            }
            .andExpect {
                status { isOk() }
                // 3 (CONNECTION) + 1 (OTHER_CONNECTION)
                jsonPath("$.scans") { value(4) }
            }
    }

    @Test
    fun `sessions outside the time window are excluded`() {
        val pastFrom = now.minus(2, ChronoUnit.HOURS).toString()
        val pastTo = now.minus(90, ChronoUnit.MINUTES).toString()

        mockMvc
            .get("/v1/analytics/attribution/funnel") {
                param("connectionId", CONNECTION_ID.toString())
                param("from", pastFrom)
                param("to", pastTo)
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.scans") { value(0) }
                jsonPath("$.ordersPlaced") { value(0) }
            }
    }

    @Test
    fun `reprocessing the same sessions does not inflate metrics`() {
        // Query the same period twice — counts must be identical
        val result1 =
            mockMvc
                .get("/v1/analytics/attribution/funnel") {
                    param("connectionId", CONNECTION_ID.toString())
                    param("from", from)
                    param("to", to)
                }
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString

        val result2 =
            mockMvc
                .get("/v1/analytics/attribution/funnel") {
                    param("connectionId", CONNECTION_ID.toString())
                    param("from", from)
                    param("to", to)
                }
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString

        org.assertj.core.api.Assertions.assertThat(result1).isEqualTo(result2)
    }

    private fun session(
        id: UUID,
        connectionId: UUID,
        status: HandoffSessionStatus,
        dataSharingMode: String,
    ) = HandoffSession(
        id = id,
        smartLinkId = UUID.randomUUID(),
        experienceVersionId = UUID.randomUUID(),
        connectionId = connectionId,
        trackingTokenHash = UUID.randomUUID().toString(),
        dataSharingMode = dataSharingMode,
        status = status,
        expiresAt = now.plus(30, ChronoUnit.MINUTES),
        createdAt = now,
        updatedAt = now,
    )
}
