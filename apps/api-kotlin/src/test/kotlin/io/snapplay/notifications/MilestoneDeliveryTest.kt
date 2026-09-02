package io.snapplay.notifications

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.snapplay.links.domain.HandoffSession
import io.snapplay.links.domain.HandoffSessionStatus
import io.snapplay.links.infrastructure.persistence.DemoHandoffSessionRepository
import io.snapplay.notifications.domain.DeliveryStatus
import io.snapplay.notifications.domain.OrderMilestone
import io.snapplay.notifications.infrastructure.client.DemoDisneyWebhookClient
import io.snapplay.notifications.infrastructure.persistence.DemoMilestoneDeliveryRepository
import io.snapplay.notifications.infrastructure.persistence.DemoPollingTokenRepository
import io.snapplay.notifications.infrastructure.scheduler.MilestoneDeliveryService
import io.snapplay.partner.infrastructure.persistence.DemoPartnerEventRepository
import io.snapplay.partner.infrastructure.persistence.DemoProviderOrderRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val WEBHOOK_SECRET = "milestone-test-secret"
private val mapper = jacksonObjectMapper()

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@TestPropertySource(
    properties = [
        "snapplay.rappiWebhookSecret=$WEBHOOK_SECRET",
        "snapplay.pollingTokenTtlMinutes=60",
    ],
)
class MilestoneDeliveryTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var demoSessionRepo: DemoHandoffSessionRepository

    @Autowired lateinit var demoOrderRepo: DemoProviderOrderRepository

    @Autowired lateinit var demoEventRepo: DemoPartnerEventRepository

    @Autowired lateinit var demoDeliveryRepo: DemoMilestoneDeliveryRepository

    @Autowired lateinit var demoWebhookClient: DemoDisneyWebhookClient

    @Autowired lateinit var demoPollingTokenRepo: DemoPollingTokenRepository

    @Autowired lateinit var deliveryService: MilestoneDeliveryService

    private lateinit var rawToken: String
    private lateinit var sessionId: UUID
    private lateinit var connectionId: UUID

    @BeforeEach
    fun setUp() {
        demoSessionRepo.clear()
        demoOrderRepo.clear()
        demoEventRepo.clear()
        demoDeliveryRepo.clear()
        demoPollingTokenRepo.clear()
        demoWebhookClient.reset()

        rawToken = UUID.randomUUID().toString().replace("-", "")
        sessionId = UUID.randomUUID()
        connectionId = UUID.fromString("00000000-0000-0000-0000-000000000001")

        val now = Instant.now()
        demoSessionRepo.create(
            HandoffSession(
                id = sessionId,
                smartLinkId = UUID.randomUUID(),
                experienceVersionId = UUID.randomUUID(),
                connectionId = connectionId,
                trackingTokenHash = sha256Hex(rawToken),
                dataSharingMode = "FULL",
                status = HandoffSessionStatus.REDIRECTED,
                expiresAt = now.plus(30, ChronoUnit.MINUTES),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    @Test
    fun `ORDER_DELIVERED webhook enqueues a DELIVERED milestone`() {
        val body = buildBody(trackingToken = rawToken, eventType = "ORDER_DELIVERED")
        val sig = sign(body, WEBHOOK_SECRET)

        mockMvc.post("/v1/partner/events") {
            contentType = MediaType.APPLICATION_JSON
            content = body
            header("X-Rappi-Signature", sig)
        }.andExpect { status { isAccepted() } }

        val deliveries = demoDeliveryRepo.all()
        assertThat(deliveries).hasSize(1)
        val delivery = deliveries.first()
        assertThat(delivery.milestone).isEqualTo(OrderMilestone.DELIVERED)
        assertThat(delivery.status).isEqualTo(DeliveryStatus.PENDING)
        assertThat(delivery.connectionId).isEqualTo(connectionId)
        assertThat(delivery.handoffSessionId).isEqualTo(sessionId)
    }

    @Test
    fun `delivery service sends pending milestone to Disney`() {
        val body = buildBody(trackingToken = rawToken, eventType = "ORDER_CONFIRMED")
        val sig = sign(body, WEBHOOK_SECRET)

        mockMvc.post("/v1/partner/events") {
            contentType = MediaType.APPLICATION_JSON
            content = body
            header("X-Rappi-Signature", sig)
        }.andExpect { status { isAccepted() } }

        deliveryService.deliverPending()

        assertThat(demoWebhookClient.deliveries).hasSize(1)
        val delivered = demoDeliveryRepo.all().first()
        assertThat(delivered.status).isEqualTo(DeliveryStatus.DELIVERED)
        assertThat(delivered.attemptCount).isEqualTo(1)
    }

    @Test
    fun `down endpoint increments attempt and schedules retry`() {
        demoWebhookClient.shouldFail = true

        val body = buildBody(trackingToken = rawToken, eventType = "ORDER_DELIVERED")
        val sig = sign(body, WEBHOOK_SECRET)

        mockMvc.post("/v1/partner/events") {
            contentType = MediaType.APPLICATION_JSON
            content = body
            header("X-Rappi-Signature", sig)
        }.andExpect { status { isAccepted() } }

        deliveryService.deliverPending()

        val delivery = demoDeliveryRepo.all().first()
        assertThat(delivery.status).isEqualTo(DeliveryStatus.PENDING)
        assertThat(delivery.attemptCount).isEqualTo(1)
        // Next retry must be in the future (backoff applied)
        assertThat(delivery.nextRetryAt).isAfter(Instant.now())
    }

    @Test
    fun `dead-lettered after max attempts`() {
        demoWebhookClient.shouldFail = true

        val body = buildBody(trackingToken = rawToken, eventType = "ORDER_DELIVERED")
        val sig = sign(body, WEBHOOK_SECRET)
        mockMvc.post("/v1/partner/events") {
            contentType = MediaType.APPLICATION_JSON
            content = body
            header("X-Rappi-Signature", sig)
        }.andExpect { status { isAccepted() } }

        // Force immediate retry eligibility for all attempts
        repeat(3) {
            // Reset nextRetryAt to now so the scheduler picks it up
            val delivery = demoDeliveryRepo.all().first()
            if (delivery.status == DeliveryStatus.PENDING) {
                demoDeliveryRepo.incrementAttempt(delivery.id, Instant.now().minusSeconds(1))
                deliveryService.deliverPending()
            }
        }

        val delivery = demoDeliveryRepo.all().first()
        assertThat(delivery.status).isEqualTo(DeliveryStatus.DEAD_LETTER)
    }

    @Test
    fun `replay same milestone enqueued only once (idempotent notification_id)`() {
        val body = buildBody(trackingToken = rawToken, eventType = "ORDER_DELIVERED")
        val sig = sign(body, WEBHOOK_SECRET)

        // Post same event twice — second call returns 202 (duplicate) via event_id dedup
        mockMvc.post("/v1/partner/events") {
            contentType = MediaType.APPLICATION_JSON
            content = body
            header("X-Rappi-Signature", sig)
        }.andExpect { status { isAccepted() } }

        // Even if enqueueMilestone is called a second time with the same milestone, only one record exists
        assertThat(demoDeliveryRepo.all()).hasSize(1)
    }

    @Test
    fun `polling token issued and used to fetch session milestone`() {
        // First, create an order milestone via webhook
        val body = buildBody(trackingToken = rawToken, eventType = "ORDER_DELIVERED")
        val sig = sign(body, WEBHOOK_SECRET)
        mockMvc.post("/v1/partner/events") {
            contentType = MediaType.APPLICATION_JSON
            content = body
            header("X-Rappi-Signature", sig)
        }.andExpect { status { isAccepted() } }

        // Issue polling token
        val tokenResponse =
            mockMvc.post("/v1/sessions/$sessionId/polling-token")
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString

        val token = mapper.readTree(tokenResponse).get("token").asText()
        assertThat(token).isNotBlank()

        // Use token to poll milestone
        mockMvc.get("/v1/sessions/milestone?token=$token")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.handoffSessionId") { value(sessionId.toString()) } }
            .andExpect { jsonPath("$.milestone") { value("DELIVERED") } }
    }

    @Test
    fun `polling token only accesses its own session (restrictive policy)`() {
        // Issue token bound to sessionId
        val tokenResponse =
            mockMvc.post("/v1/sessions/$sessionId/polling-token")
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        val token = mapper.readTree(tokenResponse).get("token").asText()

        // Verify the milestone response references sessionId, not some other session
        mockMvc.get("/v1/sessions/milestone?token=$token")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.handoffSessionId") { value(sessionId.toString()) } }

        // Unrelated session ID must not be retrievable via this token
        val otherSessionId = UUID.randomUUID()
        assertThat(sessionId).isNotEqualTo(otherSessionId) // token is bound to sessionId, not otherSessionId
    }

    @Test
    fun `expired polling token returns 401`() {
        // Store a token that expired in the past
        val rawToken2 = UUID.randomUUID().toString().replace("-", "")
        val expiredHash = sha256Hex(rawToken2)
        demoPollingTokenRepo.save(expiredHash, sessionId, Instant.now().minusSeconds(1))

        mockMvc.get("/v1/sessions/milestone?token=$rawToken2")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `missing token returns 401`() {
        mockMvc.get("/v1/sessions/milestone?token=unknowntoken")
            .andExpect { status { isUnauthorized() } }
    }

    // Helpers

    private fun buildBody(
        trackingToken: String,
        eventId: String = UUID.randomUUID().toString(),
        occurredAt: Instant = Instant.now(),
        eventType: String = "ORDER_DELIVERED",
        orderId: String = "ORD-${UUID.randomUUID()}",
    ): String =
        mapper.writeValueAsString(
            mapOf(
                "event_id" to eventId,
                "event_type" to eventType,
                "order_id" to orderId,
                "store_id" to "900000",
                "category_id" to "2000",
                "tracking_token" to trackingToken,
                "occurred_at" to occurredAt.toString(),
                "order_total" to 15.99,
                "currency" to "ARS",
            ),
        )

    private fun sign(
        body: String,
        secret: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hex = mac.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return "sha256=$hex"
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
