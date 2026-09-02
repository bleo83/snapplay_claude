package io.snapplay.notifications.infrastructure.persistence

import io.snapplay.notifications.application.port.output.SessionMilestonePort
import io.snapplay.notifications.domain.OrderMilestone
import io.snapplay.partner.infrastructure.persistence.DemoProviderOrderRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoSessionMilestonePort(
    private val orderRepo: DemoProviderOrderRepository,
) : SessionMilestonePort {
    override fun findCurrentMilestone(handoffSessionId: UUID): OrderMilestone? =
        orderRepo.orders
            .filter { it.handoffId == handoffSessionId }
            .mapNotNull { OrderMilestone.fromProviderStatus(it.status) }
            .maxByOrNull { it.ordinal }
}
