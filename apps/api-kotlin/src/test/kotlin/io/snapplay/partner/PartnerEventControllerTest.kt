package io.snapplay.partner

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.snapplay.links.domain.HandoffSession
import io.snapplay.links.domain.HandoffSessionStatus
import io.snapplay.links.infrastructure.persistence.DemoHandoffSessionRepository
import io.snapplay.partner.domain.ProviderOrderStatus
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
import org.springframework.test.web.servlet.post
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val WEBHOOK_SECRET = "integration-test-secret"
private val mapper = jacksonObjectMapper()

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@TestPropertySource(
    properties = [
        "snapplay.rappiWebhookSecret=$WEBHOOK_SECRET",
    ],
)
class PartnerEventControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var demoSessionRepo: DemoHandoffSessionRepository

    @Autowired
    lateinit var demoOrderRepo: DemoProviderOrderRepository

    private lateinit var rawToken: String
    private lateinit var sessionId: UUID
    private lateinit var connectionId: UUID

    @BeforeEach
    fun seedHandoffSession() {
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
                dataSharingMode = "AGGREGATED",
                status = HandoffSessionStatus.REDIRECTED,
                expiresAt = now.plus(30, ChronoUnit.MINUTES),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    @Test
    fun `valid signed event returns 202`() {
        val body = buildBody(trackingToken = rawToken)
        val sig = sign(body, WEBHOOK_SECRET)

        mockMvc
            .post("/v1/partner/events") {
                contentType = MediaType.APPLICATION_JSON
                content = body
                header("X-Rappi-Signature", sig)
            }
            .andExpect { status { isAccepted() } }
    }

    @Test
    fun `duplicate event_id returns 202 idempotently`() {
        val eventId = UUID.randomUUID().toString()
        val body = buildBody(trackingToken = rawToken, eventId = eventId)
        val sig = sign(body, WEBHOOK_SECRET)

        mockMvc
            .post("/v1/partner/events") {
                contentType = MediaType.APPLICATION_JSON
                content = body
                header("X-Rappi-Signature", sig)
            }
            .andExpect { status { isAccepted() } }

        // Second POST with same event_id — must still return 202
        mockMvc
            .post("/v1/partner/events") {
                contentType = MediaType.APPLICATION_JSON
                content = body
                header("X-Rappi-Signature", sig)
            }
            .andExpect { status { isAccepted() } }
    }

    @Test
    fun `invalid signature returns 401`() {
        val body = buildBody(trackingToken = rawToken)

        mockMvc
            .post("/v1/partner/events") {
                contentType = MediaType.APPLICATION_JSON
                content = body
                header("X-Rappi-Signature", "sha256=badhash")
            }
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `stale timestamp returns 422`() {
        val staleTime = Instant.now().minus(10, ChronoUnit.MINUTES)
        val body = buildBody(trackingToken = rawToken, occurredAt = staleTime)
        val sig = sign(body, WEBHOOK_SECRET)

        mockMvc
            .post("/v1/partner/events") {
                contentType = MediaType.APPLICATION_JSON
                content = body
                header("X-Rappi-Signature", sig)
            }
            .andExpect { status { isUnprocessableEntity() } }
    }

    @Test
    fun `unknown tracking token returns 422`() {
        val body = buildBody(trackingToken = "unknowntokenunknowntokenunknownto")
        val sig = sign(body, WEBHOOK_SECRET)

        mockMvc
            .post("/v1/partner/events") {
                contentType = MediaType.APPLICATION_JSON
                content = body
                header("X-Rappi-Signature", sig)
            }
            .andExpect { status { isUnprocessableEntity() } }
    }

    @Test
    fun `valid ORDER_DELIVERED event materialises a provider order with DELIVERED status`() {
        val orderId = "ORD-${UUID.randomUUID()}"
        val body = buildBody(trackingToken = rawToken, orderId = orderId)
        val sig = sign(body, WEBHOOK_SECRET)

        mockMvc
            .post("/v1/partner/events") {
                contentType = MediaType.APPLICATION_JSON
                content = body
                header("X-Rappi-Signature", sig)
            }
            .andExpect { status { isAccepted() } }

        val order = demoOrderRepo.findByRef(connectionId, orderId)
        assertThat(order).isNotNull
        assertThat(order!!.status).isEqualTo(ProviderOrderStatus.DELIVERED)
        assertThat(order.orderTotalMinor).isEqualTo(1599L)
    }

    @Test
    fun `out-of-order DELIVERED then PLACED does not revert order status`() {
        val orderId = "ORD-${UUID.randomUUID()}"
        val deliveredBody = buildBody(trackingToken = rawToken, orderId = orderId, eventType = "ORDER_DELIVERED")
        val deliveredSig = sign(deliveredBody, WEBHOOK_SECRET)

        // First event: DELIVERED
        mockMvc
            .post("/v1/partner/events") {
                contentType = MediaType.APPLICATION_JSON
                content = deliveredBody
                header("X-Rappi-Signature", deliveredSig)
            }
            .andExpect { status { isAccepted() } }

        // Re-seed session (first event converts it; need a new session for the second call)
        val rawToken2 = UUID.randomUUID().toString().replace("-", "")
        val now = Instant.now()
        demoSessionRepo.create(
            HandoffSession(
                id = UUID.randomUUID(),
                smartLinkId = UUID.randomUUID(),
                experienceVersionId = UUID.randomUUID(),
                connectionId = connectionId,
                trackingTokenHash = sha256Hex(rawToken2),
                dataSharingMode = "AGGREGATED",
                status = HandoffSessionStatus.REDIRECTED,
                expiresAt = now.plus(30, ChronoUnit.MINUTES),
                createdAt = now,
                updatedAt = now,
            ),
        )

        val placedBody = buildBody(trackingToken = rawToken2, orderId = orderId, eventType = "ORDER_PLACED")
        val placedSig = sign(placedBody, WEBHOOK_SECRET)

        // Second event: PLACED (out-of-order — should be ignored)
        mockMvc
            .post("/v1/partner/events") {
                contentType = MediaType.APPLICATION_JSON
                content = placedBody
                header("X-Rappi-Signature", placedSig)
            }
            .andExpect { status { isAccepted() } }

        val order = demoOrderRepo.findByRef(connectionId, orderId)
        assertThat(order!!.status).isEqualTo(ProviderOrderStatus.DELIVERED)
    }

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
