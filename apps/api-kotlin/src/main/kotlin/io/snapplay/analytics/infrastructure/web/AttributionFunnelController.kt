package io.snapplay.analytics.infrastructure.web

import com.fasterxml.jackson.annotation.JsonInclude
import io.snapplay.analytics.application.port.input.GetAttributionFunnelUseCase
import io.snapplay.analytics.domain.AttributionFunnel
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@RestController
@RequestMapping("/v1/analytics")
class AttributionFunnelController(
    private val getAttributionFunnelUseCase: GetAttributionFunnelUseCase,
) {
    @GetMapping("/attribution/funnel")
    fun getFunnel(
        @RequestParam(required = false) connectionId: UUID?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        from: Instant?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        to: Instant?,
    ): AttributionFunnelResponse {
        val effectiveTo = to ?: Instant.now()
        val effectiveFrom = from ?: effectiveTo.minus(30, ChronoUnit.DAYS)
        val funnel = getAttributionFunnelUseCase.get(connectionId, effectiveFrom, effectiveTo)
        return funnel.toResponse()
    }

    private fun AttributionFunnel.toResponse() =
        AttributionFunnelResponse(
            connectionId = connectionId,
            period = PeriodDto(from = from, to = to),
            scans = scans,
            redirects = redirects,
            converted = converted,
            ordersPlaced = ordersPlaced,
            ordersDelivered = ordersDelivered,
            gmvMinor = gmvMinor,
            currency = currency,
        )
}

data class PeriodDto(
    val from: Instant,
    val to: Instant,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AttributionFunnelResponse(
    val connectionId: UUID?,
    val period: PeriodDto,
    val scans: Long,
    val redirects: Long,
    val converted: Long,
    val ordersPlaced: Long,
    val ordersDelivered: Long,
    /** Null when the connection's data_sharing_mode is AGGREGATED. */
    val gmvMinor: Long?,
    val currency: String?,
)
