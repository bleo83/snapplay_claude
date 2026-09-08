package io.snapplay.settlement.infrastructure.persistence

import io.snapplay.partner.domain.ProviderOrder
import io.snapplay.partner.infrastructure.persistence.DemoProviderOrderRepository
import io.snapplay.settlement.application.port.output.SettlementOrderPort
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoSettlementOrderPort(
    private val orderRepo: DemoProviderOrderRepository,
) : SettlementOrderPort {
    override fun findOrdersByConnectionAndPeriod(
        connectionId: UUID,
        from: Instant,
        to: Instant,
    ): List<ProviderOrder> =
        orderRepo.orders
            .filter { it.connectionId == connectionId }
            .filter { !it.placedAt.isBefore(from) && it.placedAt.isBefore(to) }
}
