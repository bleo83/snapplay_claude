package io.snapplay.analytics.application.port.input

import java.time.Instant
import java.util.UUID

data class PilotDashboard(
    val rangeLabel: String,
    val currency: String,
    val freshness: Instant,
    val metrics: DashboardMetrics,
    val funnelSteps: List<FunnelStep>,
)

data class DashboardMetrics(
    val scans: Long,
    val handoffs: Long,
    val deliveredOrders: Long,
    val gmvMinor: Long,
    val conversionRate: Double,
    val revenueShareMinor: Long,
)

data class FunnelStep(
    val label: String,
    val count: Long,
    val pct: Double,
)

interface GetPilotDashboardUseCase {
    fun get(
        connectionId: UUID?,
        from: Instant?,
        to: Instant?,
    ): PilotDashboard
}
