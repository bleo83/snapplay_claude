package io.snapplay.notifications.infrastructure.scheduler

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Periodic trigger — only active in non-demo mode. Tests call [MilestoneDeliveryService.deliverPending] directly. */
@Component
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class MilestoneDeliveryScheduler(
    private val service: MilestoneDeliveryService,
) {
    @Scheduled(fixedDelay = 10_000L)
    fun run() = service.deliverPending()
}
