package io.snapplay.analytics.application.usecase

import io.snapplay.analytics.application.port.input.DashboardMetrics
import io.snapplay.analytics.application.port.input.FunnelStep
import io.snapplay.analytics.application.port.input.GetPilotDashboardUseCase
import io.snapplay.analytics.application.port.input.PilotDashboard
import io.snapplay.analytics.application.port.output.AttributionFunnelRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class GetPilotDashboardUseCaseImpl(
    private val funnelRepo: AttributionFunnelRepository,
) : GetPilotDashboardUseCase {
    companion object {
        private const val ESTIMATED_REVENUE_SHARE_PCT = 10
    }

    override fun get(
        connectionId: UUID?,
        from: Instant?,
        to: Instant?,
    ): PilotDashboard {
        val now = Instant.now()
        val effectiveTo = to ?: now
        val effectiveFrom = from ?: effectiveTo.minus(30, ChronoUnit.DAYS)

        val funnel = funnelRepo.query(connectionId, effectiveFrom, effectiveTo)

        val gmv = funnel.gmvMinor ?: 0L
        val conversionRate =
            if (funnel.scans > 0) {
                (funnel.ordersDelivered.toDouble() / funnel.scans * 100)
            } else {
                0.0
            }
        val revenueShare = gmv * ESTIMATED_REVENUE_SHARE_PCT / 100

        val steps = buildFunnelSteps(funnel.scans, funnel.redirects, funnel.ordersPlaced, funnel.ordersDelivered)

        return PilotDashboard(
            rangeLabel = formatRangeLabel(effectiveFrom, effectiveTo),
            currency = funnel.currency ?: "ARS",
            freshness = now,
            metrics =
                DashboardMetrics(
                    scans = funnel.scans,
                    handoffs = funnel.redirects,
                    deliveredOrders = funnel.ordersDelivered,
                    gmvMinor = gmv,
                    conversionRate = conversionRate,
                    revenueShareMinor = revenueShare,
                ),
            funnelSteps = steps,
        )
    }

    private fun buildFunnelSteps(
        scans: Long,
        redirects: Long,
        ordersPlaced: Long,
        ordersDelivered: Long,
    ): List<FunnelStep> {
        val base = if (scans > 0) scans.toDouble() else 1.0
        return listOf(
            FunnelStep("Escaneos", scans, 100.0),
            FunnelStep("Redirects", redirects, redirects / base * 100),
            FunnelStep("Pedidos", ordersPlaced, ordersPlaced / base * 100),
            FunnelStep("Entregados", ordersDelivered, ordersDelivered / base * 100),
        )
    }

    private fun formatRangeLabel(
        from: Instant,
        to: Instant,
    ): String {
        val days = ChronoUnit.DAYS.between(from, to)
        return if (days <= 31) "Últimos $days días" else "Período personalizado"
    }
}
