package io.snapplay.partner.infrastructure.scheduler

import io.snapplay.partner.application.port.input.ReconcileOrdersUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Runs daily reconciliation for all active connections.
 * For the pilot, the connection ID is hardcoded; in production this would iterate over all active connections.
 */
@Component
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class OrderReconciliationScheduler(
    private val reconcileUseCase: ReconcileOrdersUseCase,
) {
    private val log = LoggerFactory.getLogger(OrderReconciliationScheduler::class.java)

    companion object {
        // Pilot: single Disney × Rappi connection
        val PILOT_CONNECTION_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }

    @Scheduled(cron = "\${snapplay.reconciliation-cron:0 0 3 * * *}")
    fun run() {
        log.info("Starting scheduled order reconciliation")
        runCatching {
            reconcileUseCase.reconcile(PILOT_CONNECTION_ID)
        }.onFailure {
            log.error("Scheduled reconciliation failed for connection={}", PILOT_CONNECTION_ID, it)
        }
    }
}
