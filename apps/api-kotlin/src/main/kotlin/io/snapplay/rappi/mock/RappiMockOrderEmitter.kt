package io.snapplay.rappi.mock

import com.fasterxml.jackson.databind.ObjectMapper
import io.snapplay.config.SnapPlayProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class OrderScenario {
    DELIVERED,
    CANCELLED,
    PARTIAL_REFUND,

    /** Emits the same order_id as the most recent event for this tracking token. */
    DUPLICATE,

    /** Sets occurred_at 10 minutes in the past to simulate out-of-order delivery. */
    OUT_OF_ORDER,
}

data class EmitResult(
    val emittedPayload: Map<String, Any?>,
    val signature: String,
    val webhookStatusCode: Int,
    val webhookResponseBody: String,
)

/**
 * Builds and emits Rappi-style signed order events to the local webhook endpoint.
 *
 * Signs with HMAC-SHA256 over the JSON body using snapplay.rappiWebhookSecret,
 * mirroring what Rappi's backend would send in production.
 *
 * Not for production — active only when snapplay.rappi-mock-enabled=true.
 */
@Service
@ConditionalOnProperty(name = ["snapplay.rappi-mock-enabled"], havingValue = "true")
class RappiMockOrderEmitter(
    private val props: SnapPlayProperties,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(RappiMockOrderEmitter::class.java)
    private val restTemplate = RestTemplate()

    // Tracks last emitted order_id per tracking token to support the DUPLICATE scenario
    private val lastOrderId = ConcurrentHashMap<String, String>()

    fun emit(
        storeId: String,
        categoryId: String,
        trackingToken: String,
        scenario: OrderScenario,
        latencyMs: Long,
        invalidSignature: Boolean,
    ): EmitResult {
        if (latencyMs > 0) Thread.sleep(latencyMs.coerceAtMost(30_000))

        val orderId = resolveOrderId(trackingToken, scenario)
        val payload = buildPayload(storeId, categoryId, trackingToken, orderId, scenario)
        val body = objectMapper.writeValueAsString(payload)

        val signingKey = if (invalidSignature) "wrong-key" else props.rappiWebhookSecret
        val signature = sign(body, signingKey)

        val (statusCode, responseBody) = postToWebhook(body, signature)

        log.info(
            "Mock emitted {} order={} store={} category={} token={} status={}",
            scenario,
            orderId,
            storeId,
            categoryId,
            trackingToken,
            statusCode,
        )

        return EmitResult(
            emittedPayload = payload,
            signature = signature,
            webhookStatusCode = statusCode,
            webhookResponseBody = responseBody,
        )
    }

    private fun resolveOrderId(
        trackingToken: String,
        scenario: OrderScenario,
    ): String {
        if (scenario == OrderScenario.DUPLICATE) {
            return lastOrderId.getOrPut(trackingToken) { "ORD-${UUID.randomUUID()}" }
        }
        val orderId = "ORD-${UUID.randomUUID()}"
        lastOrderId[trackingToken] = orderId
        return orderId
    }

    private fun buildPayload(
        storeId: String,
        categoryId: String,
        trackingToken: String,
        orderId: String,
        scenario: OrderScenario,
    ): Map<String, Any?> {
        val occurredAt =
            when (scenario) {
                OrderScenario.OUT_OF_ORDER -> Instant.now().minus(10, ChronoUnit.MINUTES)
                else -> Instant.now()
            }

        val base: MutableMap<String, Any?> =
            mutableMapOf(
                "event_id" to UUID.randomUUID().toString(),
                "event_type" to scenario.toEventType(),
                "order_id" to orderId,
                "store_id" to storeId,
                "category_id" to categoryId,
                "tracking_token" to trackingToken,
                "occurred_at" to occurredAt.toString(),
                "order_total" to 15.99,
                "currency" to "ARS",
            )

        if (scenario == OrderScenario.PARTIAL_REFUND) {
            base["refunded_amount"] = 5.00
        }

        return base
    }

    private fun sign(
        body: String,
        secret: String,
    ): String {
        if (secret.isBlank()) return "sha256=unsigned"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hex = mac.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return "sha256=$hex"
    }

    private fun postToWebhook(
        body: String,
        signature: String,
    ): Pair<Int, String> {
        val url = "${props.publicBaseUrl.trimEnd('/')}/v1/partner/events"
        val headers =
            HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("X-Rappi-Signature", signature)
                set("Idempotency-Key", UUID.randomUUID().toString())
            }
        return try {
            val response = restTemplate.postForEntity(url, HttpEntity(body, headers), String::class.java)
            (response.statusCode.value()) to (response.body ?: "")
        } catch (ex: org.springframework.web.client.HttpClientErrorException) {
            ex.statusCode.value() to ex.responseBodyAsString
        } catch (ex: org.springframework.web.client.HttpServerErrorException) {
            ex.statusCode.value() to ex.responseBodyAsString
        } catch (ex: Exception) {
            HttpStatus.SERVICE_UNAVAILABLE.value() to "Could not reach webhook endpoint: ${ex.message}"
        }
    }

    private fun OrderScenario.toEventType(): String =
        when (this) {
            OrderScenario.DELIVERED -> "ORDER_DELIVERED"
            OrderScenario.CANCELLED -> "ORDER_CANCELLED"
            OrderScenario.PARTIAL_REFUND -> "ORDER_PARTIAL_REFUND"
            OrderScenario.DUPLICATE -> "ORDER_DELIVERED"
            OrderScenario.OUT_OF_ORDER -> "ORDER_DELIVERED"
        }
}
