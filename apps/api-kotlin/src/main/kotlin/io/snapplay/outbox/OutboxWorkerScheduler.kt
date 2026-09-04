package io.snapplay.outbox

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Periodic trigger — only active in non-demo mode. Tests call [OutboxWorkerService.processBatch] directly. */
@Component
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class OutboxWorkerScheduler(
    private val service: OutboxWorkerService,
) {
    @Scheduled(fixedDelay = 5_000L)
    fun run() = service.processBatch()
}
