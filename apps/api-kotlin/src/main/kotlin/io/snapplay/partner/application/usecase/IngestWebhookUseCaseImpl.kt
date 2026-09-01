package io.snapplay.partner.application.usecase

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.snapplay.common.UnauthorizedException
import io.snapplay.common.ValidationException
import io.snapplay.config.SnapPlayProperties
import io.snapplay.partner.application.port.input.IngestResult
import io.snapplay.partner.application.port.input.IngestWebhookUseCase
import io.snapplay.partner.application.port.output.HandoffSessionPort
import io.snapplay.partner.application.port.output.OutboxEventRepository
import io.snapplay.partner.application.port.output.PartnerEventRepository
import io.snapplay.partner.domain.OutboxEvent
import io.snapplay.partner.domain.PartnerEvent
import io.snapplay.partner.domain.PartnerEventStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class IngestWebhookUseCaseImpl(
    private val partnerEventRepository: PartnerEventRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val handoffSessionPort: HandoffSessionPort,
    private val objectMapper: ObjectMapper,
    private val props: SnapPlayProperties,
) : IngestWebhookUseCase {
    private val log = LoggerFactory.getLogger(IngestWebhookUseCaseImpl::class.java)

    companion object {
        private const val TIMESTAMP_TOLERANCE_MINUTES = 5L
        private const val SCHEMA_VERSION = "1.0"
        private const val AGGREGATE_TYPE = "PartnerEvent"
    }

    override fun ingest(
        rawBody: ByteArray,
        signature: String,
    ): IngestResult {
        validateSignature(rawBody, signature)

        val payload: Map<String, Any?> = objectMapper.readValue(rawBody)

        val eventId =
            payload["event_id"] as? String
                ?: throw ValidationException("Missing required field: event_id")

        // Early-exit deduplication: avoids session lookup for already-seen events
        if (partnerEventRepository.existsByEventId(eventId)) {
            log.info("Duplicate webhook event_id={} — returning idempotent 202", eventId)
            return IngestResult.DUPLICATE
        }

        val occurredAt = parseOccurredAt(payload)
        validateTimestamp(occurredAt)

        val eventType =
            payload["event_type"] as? String
                ?: throw ValidationException("Missing required field: event_type")
        val trackingToken =
            payload["tracking_token"] as? String
                ?: throw ValidationException("Missing required field: tracking_token")
        val providerOrderRef = payload["order_id"] as? String

        val tokenHash = sha256Hex(trackingToken)
        val sessionRef =
            handoffSessionPort.findByTokenHash(tokenHash)
                ?: throw ValidationException("Unknown or expired tracking token")

        val payloadJson = String(rawBody, Charsets.UTF_8)
        val event =
            PartnerEvent(
                id = UUID.randomUUID(),
                eventId = eventId,
                eventType = eventType,
                connectionId = sessionRef.connectionId,
                providerOrderRef = providerOrderRef,
                payload = payloadJson,
                signatureTimestamp = occurredAt,
                status = PartnerEventStatus.RECEIVED,
            )

        val inserted = partnerEventRepository.save(event)
        if (!inserted) {
            log.info("Duplicate webhook event_id={} — returning idempotent 202", eventId)
            return IngestResult.DUPLICATE
        }

        val outboxEvent =
            OutboxEvent(
                id = UUID.randomUUID(),
                aggregateType = AGGREGATE_TYPE,
                aggregateId = event.id,
                eventType = eventType,
                schemaVersion = SCHEMA_VERSION,
                payload = payloadJson,
                occurredAt = occurredAt,
            )
        runCatching { outboxEventRepository.enqueue(outboxEvent) }
            .onFailure { log.error("Failed to enqueue outbox event for partner_event={}", event.id, it) }

        runCatching { handoffSessionPort.markConverted(sessionRef.id) }
            .onFailure { log.warn("Failed to mark handoff session {} as CONVERTED", sessionRef.id, it) }

        log.info("Ingested webhook event_id={} type={} session={}", eventId, eventType, sessionRef.id)
        return IngestResult.ACCEPTED
    }

    private fun validateSignature(
        rawBody: ByteArray,
        signature: String,
    ) {
        if (props.rappiWebhookSecret.isBlank()) {
            // Secret not configured — skip validation (e.g. local dev without secret)
            log.warn("rappiWebhookSecret is blank; skipping HMAC validation")
            return
        }
        val expected = computeHmac(rawBody, props.rappiWebhookSecret)
        if (!MessageDigest.isEqual(expected.toByteArray(), signature.toByteArray())) {
            throw UnauthorizedException("Invalid webhook signature")
        }
    }

    private fun validateTimestamp(occurredAt: Instant) {
        val now = Instant.now()
        val diff = Math.abs(ChronoUnit.MINUTES.between(occurredAt, now))
        if (diff > TIMESTAMP_TOLERANCE_MINUTES) {
            throw ValidationException(
                "Event timestamp is outside the ${TIMESTAMP_TOLERANCE_MINUTES}-minute tolerance window (occurred_at=$occurredAt)",
            )
        }
    }

    private fun parseOccurredAt(payload: Map<String, Any?>): Instant =
        (payload["occurred_at"] as? String)
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: throw ValidationException("Missing or invalid field: occurred_at")

    private fun computeHmac(
        data: ByteArray,
        secret: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hex = mac.doFinal(data).joinToString("") { "%02x".format(it) }
        return "sha256=$hex"
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
