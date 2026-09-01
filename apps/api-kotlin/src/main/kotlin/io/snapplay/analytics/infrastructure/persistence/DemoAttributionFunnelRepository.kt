package io.snapplay.analytics.infrastructure.persistence

import io.snapplay.analytics.application.port.output.AttributionFunnelRepository
import io.snapplay.analytics.domain.AttributionFunnel
import io.snapplay.links.domain.HandoffSessionStatus
import io.snapplay.links.infrastructure.persistence.DemoHandoffSessionRepository
import io.snapplay.partner.infrastructure.persistence.DemoProviderOrderRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoAttributionFunnelRepository(
    private val sessionRepo: DemoHandoffSessionRepository,
    private val orderRepo: DemoProviderOrderRepository,
) : AttributionFunnelRepository {
    override fun query(
        connectionId: UUID?,
        from: Instant,
        to: Instant,
    ): AttributionFunnel {
        val sessions =
            sessionRepo.findAll()
                .filter { it.createdAt >= from && it.createdAt < to }
                .filter { connectionId == null || it.connectionId == connectionId }

        val sessionIds = sessions.map { it.id }.toSet()
        val orders = orderRepo.orders.filter { it.handoffId in sessionIds }

        val scans = sessions.size.toLong()
        val redirected =
            setOf(HandoffSessionStatus.REDIRECTED, HandoffSessionStatus.CONVERTED)
        val redirects = sessions.count { it.status in redirected }.toLong()
        val converted = sessions.count { it.status == HandoffSessionStatus.CONVERTED }.toLong()
        val ordersPlaced = orders.size.toLong()
        val ordersDelivered =
            orders.count { it.status.name == "DELIVERED" }.toLong()

        // GMV only for non-AGGREGATED sessions; null when all orders are AGGREGATED
        val nonAggregatedOrders =
            orders.filter { order ->
                val session = sessions.firstOrNull { it.id == order.handoffId }
                session?.dataSharingMode != "AGGREGATED"
            }
        val gmvMinor = nonAggregatedOrders.sumOf { it.orderTotalMinor }.takeIf { nonAggregatedOrders.isNotEmpty() }

        val currency = orders.map { it.currency }.firstOrNull()

        return AttributionFunnel(
            connectionId = connectionId,
            from = from,
            to = to,
            scans = scans,
            redirects = redirects,
            converted = converted,
            ordersPlaced = ordersPlaced,
            ordersDelivered = ordersDelivered,
            gmvMinor = gmvMinor,
            currency = currency,
        )
    }
}
