package io.snapplay.analytics.infrastructure.web

import io.snapplay.analytics.application.port.input.FunnelStep
import io.snapplay.analytics.application.port.input.GetPilotDashboardUseCase
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/v1")
class DashboardController(
    private val getPilotDashboard: GetPilotDashboardUseCase,
) {
    @GetMapping("/dashboard")
    fun getDashboard(
        @RequestParam(required = false) connectionId: UUID?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        from: Instant?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        to: Instant?,
    ): DashboardResponse {
        val dashboard = getPilotDashboard.get(connectionId, from, to)
        return DashboardResponse(
            rangeLabel = dashboard.rangeLabel,
            currency = dashboard.currency,
            freshness = dashboard.freshness,
            metrics =
                MetricsDto(
                    scans = dashboard.metrics.scans,
                    handoffs = dashboard.metrics.handoffs,
                    deliveredOrders = dashboard.metrics.deliveredOrders,
                    gmvMinor = dashboard.metrics.gmvMinor,
                    conversionRate = dashboard.metrics.conversionRate,
                    revenueShareMinor = dashboard.metrics.revenueShareMinor,
                ),
            funnelSteps = dashboard.funnelSteps,
            experiences = emptyList(),
            recentOrders = emptyList(),
        )
    }
}

data class DashboardResponse(
    val rangeLabel: String,
    val currency: String,
    val freshness: Instant,
    val metrics: MetricsDto,
    val funnelSteps: List<FunnelStep>,
    val experiences: List<Any>,
    val recentOrders: List<Any>,
)

data class MetricsDto(
    val scans: Long,
    val handoffs: Long,
    val deliveredOrders: Long,
    val gmvMinor: Long,
    val conversionRate: Double,
    val revenueShareMinor: Long,
)
