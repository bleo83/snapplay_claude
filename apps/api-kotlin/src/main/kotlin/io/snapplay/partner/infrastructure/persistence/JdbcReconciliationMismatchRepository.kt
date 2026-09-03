package io.snapplay.partner.infrastructure.persistence

import io.snapplay.partner.application.port.output.ReconciliationMismatchRepository
import io.snapplay.partner.domain.ReconciliationMismatch
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcReconciliationMismatchRepository(
    private val jdbc: JdbcTemplate,
) : ReconciliationMismatchRepository {
    override fun save(mismatch: ReconciliationMismatch) {
        jdbc.update(
            """
            INSERT INTO reconciliation_mismatches
                (id, connection_id, provider_order_ref, mismatch_type, snap_play_value, rappi_value, detected_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            mismatch.id,
            mismatch.connectionId,
            mismatch.providerOrderRef,
            mismatch.mismatchType.name,
            mismatch.snapPlayValue,
            mismatch.rappiValue,
            Timestamp.from(mismatch.detectedAt),
        )
    }
}
