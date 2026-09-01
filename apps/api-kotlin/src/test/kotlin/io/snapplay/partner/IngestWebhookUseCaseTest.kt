package io.snapplay.partner

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.snapplay.common.UnauthorizedException
import io.snapplay.common.ValidationException
import io.snapplay.config.SnapPlayProperties
import io.snapplay.partner.application.port.input.IngestResult
import io.snapplay.partner.application.port.output.HandoffSessionPort
import io.snapplay.partner.application.port.output.OutboxEventRepository
import io.snapplay.partner.application.port.output.PartnerEventRepository
import io.snapplay.partner.application.port.output.ProviderOrderRepository
import io.snapplay.partner.application.port.output.ProviderOrderUpsert
import io.snapplay.partner.application.usecase.IngestWebhookUseCaseImpl
import io.snapplay.partner.domain.HandoffSessionRef
import io.snapplay.partner.domain.OutboxEvent
import io.snapplay.partner.domain.PartnerEvent
import io.snapplay.partner.domain.ProviderOrderStatus
import io.snapplay.partner.domain.UpsertOutcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val SECRET = "test-webhook-secret"

private val SESSION_ID = UUID.randomUUID()
private val CONNECTION_ID = UUID.randomUUID()
private val SESSION_REF = HandoffSessionRef(id = SESSION_ID, connectionId = CONNECTION_ID)

// A 32-char hex tracking token (no dashes)
private val RAW_TOKEN = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"

private val objectMapper = jacksonObjectMapper()

private fun hmacSha256(
    body: String,
    secret: String,
): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return "sha256=" + mac.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

private fun sha256Hex(input: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    return digest.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

private fun buildPayload(
    trackingToken: String = RAW_TOKEN,
    occurredAt: Instant = Instant.now(),
    eventId: String = UUID.randomUUID().toString(),
    eventType: String = "ORDER_DELIVERED",
): String =
    objectMapper.writeValueAsString(
        mapOf(
            "event_id" to eventId,
            "event_type" to eventType,
            "order_id" to "ORD-${UUID.randomUUID()}",
            "store_id" to "900000",
            "category_id" to "2000",
            "tracking_token" to trackingToken,
            "occurred_at" to occurredAt.toString(),
            "order_total" to 15.99,
            "currency" to "ARS",
        ),
    )

// --- Stubs ---

private class FakePartnerEventRepository : PartnerEventRepository {
    private val store = ConcurrentHashMap<String, PartnerEvent>()
    val saved get(): List<PartnerEvent> = store.values.toList()

    override fun existsByEventId(eventId: String): Boolean = store.containsKey(eventId)

    override fun save(event: PartnerEvent): Boolean = store.putIfAbsent(event.eventId, event) == null
}

private class FakeOutboxEventRepository : OutboxEventRepository {
    val enqueued = mutableListOf<OutboxEvent>()

    override fun enqueue(event: OutboxEvent) {
        enqueued.add(event)
    }
}

private class FakeHandoffSessionPort(
    private val ref: HandoffSessionRef? = SESSION_REF,
) : HandoffSessionPort {
    val convertedIds = mutableListOf<UUID>()

    override fun findByTokenHash(tokenHash: String): HandoffSessionRef? = ref

    override fun markConverted(id: UUID) {
        convertedIds.add(id)
    }
}

private class FakeProviderOrderRepository : ProviderOrderRepository {
    val upserts = mutableListOf<ProviderOrderUpsert>()

    override fun upsert(
        upsert: ProviderOrderUpsert,
        partnerEventId: UUID,
    ): UpsertOutcome {
        upserts.add(upsert)
        return UpsertOutcome.Created(UUID.randomUUID())
    }
}

// --- Tests ---

class IngestWebhookUseCaseTest {
    private lateinit var eventRepo: FakePartnerEventRepository
    private lateinit var outboxRepo: FakeOutboxEventRepository
    private lateinit var sessionPort: FakeHandoffSessionPort
    private lateinit var orderRepo: FakeProviderOrderRepository
    private lateinit var useCase: IngestWebhookUseCaseImpl

    @BeforeEach
    fun setUp() {
        eventRepo = FakePartnerEventRepository()
        outboxRepo = FakeOutboxEventRepository()
        sessionPort = FakeHandoffSessionPort()
        orderRepo = FakeProviderOrderRepository()
        useCase =
            IngestWebhookUseCaseImpl(
                eventRepo,
                outboxRepo,
                sessionPort,
                orderRepo,
                objectMapper,
                SnapPlayProperties(rappiWebhookSecret = SECRET),
            )
    }

    @Test
    fun `valid event returns ACCEPTED and persists to event repo and outbox`() {
        val body = buildPayload()
        val sig = hmacSha256(body, SECRET)

        val result = useCase.ingest(body.toByteArray(), sig)

        assertThat(result).isEqualTo(IngestResult.ACCEPTED)
        assertThat(eventRepo.saved).hasSize(1)
        assertThat(outboxRepo.enqueued).hasSize(1)
        assertThat(sessionPort.convertedIds).containsExactly(SESSION_ID)
    }

    @Test
    fun `valid event stores connection_id from handoff session`() {
        val body = buildPayload()
        val sig = hmacSha256(body, SECRET)

        useCase.ingest(body.toByteArray(), sig)

        assertThat(eventRepo.saved.first().connectionId).isEqualTo(CONNECTION_ID)
    }

    @Test
    fun `invalid HMAC signature throws UnauthorizedException`() {
        val body = buildPayload()

        assertThrows<UnauthorizedException> {
            useCase.ingest(body.toByteArray(), "sha256=deadbeef")
        }
        assertThat(eventRepo.saved).isEmpty()
    }

    @Test
    fun `stale timestamp beyond tolerance throws ValidationException`() {
        val staleTime = Instant.now().minus(10, ChronoUnit.MINUTES)
        val body = buildPayload(occurredAt = staleTime)
        val sig = hmacSha256(body, SECRET)

        assertThrows<ValidationException> {
            useCase.ingest(body.toByteArray(), sig)
        }
        assertThat(eventRepo.saved).isEmpty()
    }

    @Test
    fun `unknown tracking token throws ValidationException`() {
        val useCaseNoSession =
            IngestWebhookUseCaseImpl(
                eventRepo,
                outboxRepo,
                FakeHandoffSessionPort(ref = null),
                orderRepo,
                objectMapper,
                SnapPlayProperties(rappiWebhookSecret = SECRET),
            )
        val body = buildPayload()
        val sig = hmacSha256(body, SECRET)

        assertThrows<ValidationException> {
            useCaseNoSession.ingest(body.toByteArray(), sig)
        }
    }

    @Test
    fun `duplicate event_id returns DUPLICATE without re-persisting`() {
        val eventId = UUID.randomUUID().toString()
        val body = buildPayload(eventId = eventId)
        val sig = hmacSha256(body, SECRET)

        val first = useCase.ingest(body.toByteArray(), sig)
        val second = useCase.ingest(body.toByteArray(), sig)

        assertThat(first).isEqualTo(IngestResult.ACCEPTED)
        assertThat(second).isEqualTo(IngestResult.DUPLICATE)
        assertThat(eventRepo.saved).hasSize(1)
        // Outbox and CONVERTED only for the first ingestion
        assertThat(outboxRepo.enqueued).hasSize(1)
        assertThat(sessionPort.convertedIds).hasSize(1)
    }

    @Test
    fun `blank secret skips HMAC validation`() {
        val useCaseNoSecret =
            IngestWebhookUseCaseImpl(
                eventRepo,
                outboxRepo,
                sessionPort,
                orderRepo,
                objectMapper,
                SnapPlayProperties(rappiWebhookSecret = ""),
            )
        val body = buildPayload()

        val result = useCaseNoSecret.ingest(body.toByteArray(), "sha256=any-value")

        assertThat(result).isEqualTo(IngestResult.ACCEPTED)
    }

    @Test
    fun `outbox event has correct aggregate type and event type`() {
        val body = buildPayload(eventType = "ORDER_CANCELLED")
        val sig = hmacSha256(body, SECRET)

        useCase.ingest(body.toByteArray(), sig)

        val outbox = outboxRepo.enqueued.first()
        assertThat(outbox.aggregateType).isEqualTo("PartnerEvent")
        assertThat(outbox.eventType).isEqualTo("ORDER_CANCELLED")
        assertThat(outbox.aggregateId).isEqualTo(eventRepo.saved.first().id)
    }

    @Test
    fun `valid ORDER_DELIVERED event triggers provider order upsert`() {
        val body = buildPayload(eventType = "ORDER_DELIVERED")
        val sig = hmacSha256(body, SECRET)

        useCase.ingest(body.toByteArray(), sig)

        assertThat(orderRepo.upserts).hasSize(1)
        assertThat(orderRepo.upserts.first().newStatus).isEqualTo(ProviderOrderStatus.DELIVERED)
    }

    @Test
    fun `order total is converted to minor units`() {
        val body = buildPayload()
        val sig = hmacSha256(body, SECRET)

        useCase.ingest(body.toByteArray(), sig)

        // Mock payload has order_total = 15.99 → 1599 minor units
        assertThat(orderRepo.upserts.first().orderTotalMinor).isEqualTo(1599L)
    }

    @Test
    fun `order upsert uses connection_id from handoff session`() {
        val body = buildPayload()
        val sig = hmacSha256(body, SECRET)

        useCase.ingest(body.toByteArray(), sig)

        assertThat(orderRepo.upserts.first().connectionId).isEqualTo(CONNECTION_ID)
    }
}
