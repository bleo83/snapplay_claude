package io.snapplay.outbox

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

/**
 * Polls the outbox_events table, dispatches to registered [OutboxHandler]s,
 * and manages retry/dead-letter state.
 *
 * Multi-instance safe via `SELECT FOR UPDATE SKIP LOCKED`.
 */
@Service
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class OutboxWorkerService(
    private val jdbc: JdbcTemplate,
    handlers: List<OutboxHandler>,
) {
    private val log = LoggerFactory.getLogger(OutboxWorkerService::class.java)
    private val handlerMap: Map<String, OutboxHandler> = handlers.associateBy { it.supportedEventType }

    companion object {
        private const val BATCH_SIZE = 20
        private const val MAX_ATTEMPTS = 5
        private val BACKOFF =
            listOf(
                Duration.ZERO,
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                Duration.ofMinutes(2),
                Duration.ofMinutes(10),
            )
        private const val JITTER_CEILING_MS = 5_000L
    }

    fun processBatch() {
        val events = claimBatch()
        for (event in events) {
            processEvent(event)
        }
    }

    private fun claimBatch(): List<OutboxRow> =
        jdbc.query(
            """
            SELECT id, aggregate_type, aggregate_id, event_type, payload, attempts
            FROM outbox_events
            WHERE status = 'PENDING' AND next_retry_at <= now()
            ORDER BY next_retry_at
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """.trimIndent(),
            { rs, _ ->
                OutboxRow(
                    id = UUID.fromString(rs.getString("id")),
                    aggregateType = rs.getString("aggregate_type"),
                    aggregateId = UUID.fromString(rs.getString("aggregate_id")),
                    eventType = rs.getString("event_type"),
                    payload = rs.getString("payload"),
                    attempts = rs.getInt("attempts"),
                )
            },
            BATCH_SIZE,
        )

    @Transactional
    fun processEvent(event: OutboxRow) {
        val handler = handlerMap[event.eventType]
        if (handler == null) {
            log.warn("No handler registered for event_type={} — skipping outbox event {}", event.eventType, event.id)
            return
        }

        val success =
            runCatching {
                handler.handle(event.aggregateType, event.aggregateId, event.eventType, event.payload)
            }.getOrElse {
                log.warn("Handler threw for outbox event {} type={}", event.id, event.eventType, it)
                false
            }

        if (success) {
            markSucceeded(event.id)
            log.debug("Outbox event {} succeeded", event.id)
        } else {
            val nextAttempt = event.attempts + 1
            if (nextAttempt >= MAX_ATTEMPTS) {
                markDeadLetter(event.id, nextAttempt)
                log.error("Dead-lettered outbox event {} after {} attempts", event.id, nextAttempt)
            } else {
                scheduleRetry(event.id, nextAttempt)
                log.warn("Will retry outbox event {} attempt={}", event.id, nextAttempt)
            }
        }
    }

    private fun markSucceeded(id: UUID) {
        jdbc.update(
            "UPDATE outbox_events SET status = 'SUCCEEDED', published_at = now(), attempts = attempts + 1 WHERE id = ?",
            id,
        )
    }

    private fun markDeadLetter(
        id: UUID,
        attempts: Int,
    ) {
        jdbc.update(
            "UPDATE outbox_events SET status = 'DEAD_LETTER', attempts = ? WHERE id = ?",
            attempts,
            id,
        )
    }

    private fun scheduleRetry(
        id: UUID,
        nextAttempt: Int,
    ) {
        val backoff = BACKOFF.getOrElse(nextAttempt) { Duration.ofMinutes(10) }
        val jitter = Duration.ofMillis(ThreadLocalRandom.current().nextLong(0, JITTER_CEILING_MS))
        val nextRetryAt = Instant.now().plus(backoff).plus(jitter)
        jdbc.update(
            "UPDATE outbox_events SET attempts = ?, next_retry_at = ? WHERE id = ?",
            nextAttempt,
            Timestamp.from(nextRetryAt),
            id,
        )
    }

    /** Used by the retry admin endpoint to re-enqueue dead-lettered events. */
    fun retryDeadLetter(id: UUID): Boolean {
        val updated =
            jdbc.update(
                "UPDATE outbox_events SET status = 'PENDING', attempts = 0, next_retry_at = now() WHERE id = ? AND status = 'DEAD_LETTER'",
                id,
            )
        return updated > 0
    }

    data class OutboxRow(
        val id: UUID,
        val aggregateType: String,
        val aggregateId: UUID,
        val eventType: String,
        val payload: String,
        val attempts: Int,
    )
}
