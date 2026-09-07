package io.snapplay.links.infrastructure.persistence

import io.snapplay.links.application.port.output.HandoffSessionRepository
import io.snapplay.links.domain.HandoffSession
import io.snapplay.links.domain.HandoffSessionStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcHandoffSessionRepository(
    private val jdbc: JdbcTemplate,
) : HandoffSessionRepository {
    override fun create(session: HandoffSession) {
        jdbc.update(
            """
            INSERT INTO handoff_sessions
                (id, smart_link_id, experience_version_id, connection_id,
                 tracking_token_hash, data_sharing_mode, status, is_bot, expires_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?::data_sharing_mode, ?, ?, ?, ?, ?)
            """.trimIndent(),
            session.id,
            session.smartLinkId,
            session.experienceVersionId,
            session.connectionId,
            session.trackingTokenHash,
            session.dataSharingMode,
            session.status.name,
            session.isBot,
            Timestamp.from(session.expiresAt),
            Timestamp.from(session.createdAt),
            Timestamp.from(session.updatedAt),
        )
    }

    override fun updateStatus(
        id: UUID,
        status: HandoffSessionStatus,
    ) {
        // Predicate prevents backward transitions from terminal states without locking
        jdbc.update(
            """
            UPDATE handoff_sessions
               SET status = ?, updated_at = now()
             WHERE id = ?
               AND status NOT IN ('CONVERTED', 'EXPIRED', 'FAILED')
            """.trimIndent(),
            status.name,
            id,
        )
    }
}
