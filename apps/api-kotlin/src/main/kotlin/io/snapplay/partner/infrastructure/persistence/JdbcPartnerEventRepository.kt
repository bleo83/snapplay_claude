package io.snapplay.partner.infrastructure.persistence

import io.snapplay.partner.application.port.output.PartnerEventRepository
import io.snapplay.partner.domain.PartnerEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcPartnerEventRepository(
    private val jdbc: JdbcTemplate,
) : PartnerEventRepository {
    override fun existsByEventId(eventId: String): Boolean =
        (jdbc.queryForObject("SELECT COUNT(*) FROM partner_events WHERE event_id = ?", Int::class.java, eventId) ?: 0) > 0

    override fun save(event: PartnerEvent): Boolean =
        try {
            jdbc.update(
                """
                INSERT INTO partner_events
                    (id, event_id, event_type, connection_id, provider_order_ref,
                     payload, signature_timestamp, status, error_detail)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                """.trimIndent(),
                event.id,
                event.eventId,
                event.eventType,
                event.connectionId,
                event.providerOrderRef,
                event.payload,
                Timestamp.from(event.signatureTimestamp),
                event.status.name,
                event.errorDetail,
            )
            true
        } catch (_: DuplicateKeyException) {
            false
        }
}
