package io.snapplay.partner.infrastructure.persistence

import io.snapplay.partner.application.port.output.OutboxEventRepository
import io.snapplay.partner.domain.OutboxEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcOutboxEventRepository(
    private val jdbc: JdbcTemplate,
) : OutboxEventRepository {
    override fun enqueue(event: OutboxEvent) {
        jdbc.update(
            """
            INSERT INTO outbox_events
                (id, aggregate_type, aggregate_id, event_type, schema_version, payload, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
            """.trimIndent(),
            event.id,
            event.aggregateType,
            event.aggregateId,
            event.eventType,
            event.schemaVersion,
            event.payload,
            Timestamp.from(event.occurredAt),
        )
    }
}
