package io.snapplay.partner.infrastructure.persistence

import io.snapplay.partner.application.port.output.HandoffSessionPort
import io.snapplay.partner.domain.HandoffSessionRef
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcHandoffSessionPort(
    private val jdbc: JdbcTemplate,
) : HandoffSessionPort {
    override fun findByTokenHash(tokenHash: String): HandoffSessionRef? =
        jdbc.query(
            """
            SELECT id, connection_id
              FROM handoff_sessions
             WHERE tracking_token_hash = ?
               AND status NOT IN ('CONVERTED', 'EXPIRED', 'FAILED')
               AND expires_at > now()
            LIMIT 1
            """.trimIndent(),
            { rs, _ ->
                HandoffSessionRef(
                    id = UUID.fromString(rs.getString("id")),
                    connectionId = UUID.fromString(rs.getString("connection_id")),
                )
            },
            tokenHash,
        ).firstOrNull()

    override fun markConverted(id: UUID) {
        jdbc.update(
            """
            UPDATE handoff_sessions
               SET status = 'CONVERTED', updated_at = now()
             WHERE id = ?
               AND status NOT IN ('CONVERTED', 'EXPIRED', 'FAILED')
            """.trimIndent(),
            id,
        )
    }
}
