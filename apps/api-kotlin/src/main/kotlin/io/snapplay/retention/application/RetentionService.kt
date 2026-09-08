package io.snapplay.retention.application

import io.snapplay.retention.domain.DataClass
import io.snapplay.retention.domain.RetentionAction
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Instant

/**
 * Executes data retention policies: anonymizes or deletes expired data.
 *
 * Rules:
 * - Financial evidence (ledger, audit) is NEVER deleted or anonymized.
 * - Anonymizable data has PII fields nulled/hashed but retains aggregate-level info.
 * - Non-anonymizable expired data is deleted outright.
 */
@Service
class RetentionService(
    private val jdbc: JdbcTemplate?,
) {
    private val log = LoggerFactory.getLogger(RetentionService::class.java)

    fun executeAll(): List<RetentionAction> {
        if (jdbc == null) {
            log.info("No database — skipping retention (demo mode)")
            return emptyList()
        }

        val now = Instant.now()
        return DataClass.entries
            .filter { it.retention.toDays() < 2555 } // skip permanent-retention classes
            .map { dc -> execute(dc, now) }
    }

    fun execute(
        dataClass: DataClass,
        now: Instant,
    ): RetentionAction {
        val cutoff = now.minus(dataClass.retention)
        val rows =
            if (dataClass.anonymizable) {
                anonymize(dataClass, cutoff)
            } else {
                delete(dataClass, cutoff)
            }

        log.info("Retention: {} {} rows for {} (cutoff={})", if (dataClass.anonymizable) "anonymized" else "deleted", rows, dataClass.tableName, cutoff)

        return RetentionAction(
            dataClass = dataClass,
            action = if (dataClass.anonymizable) "ANONYMIZE" else "DELETE",
            rowsAffected = rows,
            executedAt = now,
        )
    }

    private fun anonymize(
        dataClass: DataClass,
        cutoff: Instant,
    ): Int =
        when (dataClass) {
            DataClass.HANDOFF_SESSION ->
                jdbc!!.update(
                    """
                    UPDATE handoff_sessions
                    SET tracking_token_hash = 'ANONYMIZED',
                        data_sharing_mode = 'AGGREGATED'
                    WHERE created_at < ? AND tracking_token_hash != 'ANONYMIZED'
                    """.trimIndent(),
                    Timestamp.from(cutoff),
                )
            DataClass.PARTNER_EVENT ->
                jdbc!!.update(
                    """
                    UPDATE partner_events
                    SET payload = '{"anonymized":true}'
                    WHERE signature_timestamp < ? AND payload != '{"anonymized":true}'
                    """.trimIndent(),
                    Timestamp.from(cutoff),
                )
            else -> 0
        }

    private fun delete(
        dataClass: DataClass,
        cutoff: Instant,
    ): Int =
        when (dataClass) {
            DataClass.POLLING_TOKEN ->
                jdbc!!.update("DELETE FROM handoff_polling_tokens WHERE expires_at < ?", Timestamp.from(cutoff))
            DataClass.OUTBOX_EVENT ->
                jdbc!!.update("DELETE FROM outbox_events WHERE created_at < ? AND status IN ('SUCCEEDED', 'DEAD_LETTER')", Timestamp.from(cutoff))
            DataClass.MILESTONE_DELIVERY ->
                jdbc!!.update("DELETE FROM milestone_deliveries WHERE created_at < ? AND status IN ('DELIVERED', 'DEAD_LETTER')", Timestamp.from(cutoff))
            else -> 0
        }
}
