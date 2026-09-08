package io.snapplay.retention.infrastructure

import io.snapplay.retention.application.RetentionService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class RetentionScheduler(
    private val retentionService: RetentionService,
) {
    private val log = LoggerFactory.getLogger(RetentionScheduler::class.java)

    /** Runs daily at 4 AM — after reconciliation (3 AM) completes. */
    @Scheduled(cron = "\${snapplay.retention-cron:0 0 4 * * *}")
    fun run() {
        log.info("Starting scheduled retention job")
        val results = retentionService.executeAll()
        val total = results.sumOf { it.rowsAffected }
        log.info("Retention complete: {} total rows processed", total)
    }
}
