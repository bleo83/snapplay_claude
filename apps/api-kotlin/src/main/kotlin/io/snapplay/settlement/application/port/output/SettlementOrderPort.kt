package io.snapplay.settlement.application.port.output

import io.snapplay.partner.domain.ProviderOrder
import java.time.Instant
import java.util.UUID

interface SettlementOrderPort {
    fun findOrdersByConnectionAndPeriod(
        connectionId: UUID,
        from: Instant,
        to: Instant,
    ): List<ProviderOrder>
}
