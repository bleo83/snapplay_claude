package io.snapplay.partner.infrastructure.persistence

import io.snapplay.partner.application.port.output.ReconciliationCheckpointRepository
import io.snapplay.partner.domain.ReconciliationCheckpoint
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcReconciliationCheckpointRepository(
    private val jdbc: JdbcTemplate,
) : ReconciliationCheckpointRepository {
    override fun findByConnectionId(connectionId: UUID): ReconciliationCheckpoint? =
        jdbc.query(
            "SELECT id, connection_id, last_reconciled_at FROM reconciliation_checkpoints WHERE connection_id = ?",
            { rs, _ ->
                ReconciliationCheckpoint(
                    id = UUID.fromString(rs.getString("id")),
                    connectionId = UUID.fromString(rs.getString("connection_id")),
                    lastReconciledAt = rs.getTimestamp("last_reconciled_at").toInstant(),
                )
            },
            connectionId,
        ).firstOrNull()

    override fun advance(
        connectionId: UUID,
        reconciledAt: Instant,
    ) {
        jdbc.update(
            """
            INSERT INTO reconciliation_checkpoints (connection_id, last_reconciled_at)
            VALUES (?, ?)
            ON CONFLICT (connection_id)
            DO UPDATE SET last_reconciled_at = EXCLUDED.last_reconciled_at, updated_at = now()
            """.trimIndent(),
            connectionId,
            Timestamp.from(reconciledAt),
        )
    }
}
