package io.snapplay.partner.infrastructure.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.snapplay.config.SnapPlayProperties
import io.snapplay.partner.application.port.output.RappiOrdersClient
import io.snapplay.partner.domain.RappiOrderPage
import io.snapplay.partner.domain.RappiOrderSnapshot
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToLong

@Component
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class HttpRappiOrdersClient(
    restClientBuilder: RestClient.Builder,
    private val props: SnapPlayProperties,
    private val objectMapper: ObjectMapper,
) : RappiOrdersClient {
    private val log = LoggerFactory.getLogger(HttpRappiOrdersClient::class.java)
    private val restClient = restClientBuilder.build()

    override fun fetchOrders(
        connectionId: UUID,
        from: Instant,
        to: Instant,
        cursor: String?,
    ): RappiOrderPage {
        if (props.rappiApiBaseUrl.isBlank()) {
            log.warn("rappiApiBaseUrl not configured — returning empty page")
            return RappiOrderPage(orders = emptyList(), nextCursor = null)
        }

        val uri =
            "${props.rappiApiBaseUrl}/api/v1/orders?" +
                "from=$from&to=$to" +
                (cursor?.let { "&cursor=$it" } ?: "")

        val body =
            restClient
                .get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer ${props.rappiApiKey}")
                .retrieve()
                .body(String::class.java) ?: return RappiOrderPage(emptyList(), null)

        return parseResponse(body)
    }

    private fun parseResponse(body: String): RappiOrderPage {
        val json: Map<String, Any?> = objectMapper.readValue(body)

        @Suppress("UNCHECKED_CAST")
        val rawOrders = (json["orders"] as? List<Map<String, Any?>>) ?: emptyList()
        val orders =
            rawOrders.map { raw ->
                val totalMinor = ((raw["order_total"] as? Number)?.toDouble() ?: 0.0).times(100).roundToLong()
                RappiOrderSnapshot(
                    orderRef = raw["order_id"] as? String ?: "",
                    status = raw["status"] as? String ?: "PLACED",
                    currency = raw["currency"] as? String ?: "ARS",
                    totalMinor = totalMinor,
                    placedAt = (raw["placed_at"] as? String)?.let { Instant.parse(it) } ?: Instant.now(),
                    deliveredAt = (raw["delivered_at"] as? String)?.let { Instant.parse(it) },
                )
            }
        val nextCursor = json["next_cursor"] as? String
        return RappiOrderPage(orders = orders, nextCursor = nextCursor)
    }
}
