package io.snapplay.notifications.infrastructure.persistence

import io.snapplay.notifications.application.port.output.PollingTokenRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcPollingTokenRepository(
    private val jdbc: JdbcTemplate,
) : PollingTokenRepository {
    override fun save(
        tokenHash: String,
        handoffSessionId: UUID,
        expiresAt: Instant,
    ) {
        jdbc.update(
            "INSERT INTO handoff_polling_tokens (token_hash, handoff_session_id, expires_at) VALUES (?, ?, ?)",
            tokenHash,
            handoffSessionId,
            Timestamp.from(expiresAt),
        )
    }

    override fun findValidSession(
        tokenHash: String,
        now: Instant,
    ): UUID? =
        jdbc.query(
            "SELECT handoff_session_id FROM handoff_polling_tokens WHERE token_hash = ? AND expires_at > ?",
            { rs, _ -> UUID.fromString(rs.getString("handoff_session_id")) },
            tokenHash,
            Timestamp.from(now),
        ).firstOrNull()
}
