package io.snapplay.settlement.infrastructure.persistence

import io.snapplay.settlement.application.port.output.SettlementRepository
import io.snapplay.settlement.domain.ExceptionStatus
import io.snapplay.settlement.domain.SettlementException
import io.snapplay.settlement.domain.SettlementExceptionType
import io.snapplay.settlement.domain.SettlementReport
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcSettlementRepository(
    private val jdbc: JdbcTemplate,
) : SettlementRepository {
    override fun createReport(report: SettlementReport): SettlementReport {
        jdbc.update(
            "INSERT INTO settlement_reports (id, connection_id, period_from, period_to, line_count) VALUES (?, ?, ?, ?, ?)",
            report.id,
            report.connectionId,
            Timestamp.from(report.periodFrom),
            Timestamp.from(report.periodTo),
            report.lineCount,
        )
        return report
    }

    override fun saveException(exception: SettlementException): Boolean =
        try {
            jdbc.update(
                """
                INSERT INTO settlement_exceptions
                    (id, report_id, connection_id, provider_order_ref, exception_type,
                     expected_value, reported_value, tolerance_applied_minor, status, detected_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                exception.id,
                exception.reportId,
                exception.connectionId,
                exception.providerOrderRef,
                exception.exceptionType.name,
                exception.expectedValue,
                exception.reportedValue,
                exception.toleranceAppliedMinor,
                exception.status.name,
                Timestamp.from(exception.detectedAt),
            )
            true
        } catch (_: DuplicateKeyException) {
            false
        }

    override fun findExceptionsByReport(reportId: UUID): List<SettlementException> =
        jdbc.query("SELECT * FROM settlement_exceptions WHERE report_id = ? ORDER BY detected_at", { rs, _ -> rs.toException() }, reportId)

    @Suppress("SqlSourceToSinkFlow")
    override fun findExceptionsByConnection(
        connectionId: UUID,
        status: ExceptionStatus?,
    ): List<SettlementException> {
        val params = mutableListOf<Any>(connectionId)
        val sql =
            buildString {
                append("SELECT * FROM settlement_exceptions WHERE connection_id = ?")
                if (status != null) {
                    append(" AND status = ?")
                    params.add(status.name)
                }
                append(" ORDER BY detected_at DESC")
            }
        return jdbc.query(sql, { rs, _ -> rs.toException() }, *params.toTypedArray())
    }

    override fun resolveException(
        id: UUID,
        status: ExceptionStatus,
        adjustmentId: UUID?,
        note: String,
        resolvedBy: UUID,
        resolvedAt: Instant,
    ) {
        jdbc.update(
            "UPDATE settlement_exceptions SET status = ?, resolution_adjustment_id = ?, resolution_note = ?, resolved_by = ?, resolved_at = ? WHERE id = ?",
            status.name,
            adjustmentId,
            note,
            resolvedBy,
            Timestamp.from(resolvedAt),
            id,
        )
    }

    private fun ResultSet.toException() =
        SettlementException(
            id = UUID.fromString(getString("id")),
            reportId = UUID.fromString(getString("report_id")),
            connectionId = UUID.fromString(getString("connection_id")),
            providerOrderRef = getString("provider_order_ref"),
            exceptionType = SettlementExceptionType.valueOf(getString("exception_type")),
            expectedValue = getString("expected_value"),
            reportedValue = getString("reported_value"),
            toleranceAppliedMinor = getLong("tolerance_applied_minor").takeIf { !wasNull() },
            status = ExceptionStatus.valueOf(getString("status")),
            resolutionAdjustmentId = getString("resolution_adjustment_id")?.let { UUID.fromString(it) },
            resolutionNote = getString("resolution_note"),
            resolvedBy = getString("resolved_by")?.let { UUID.fromString(it) },
            resolvedAt = getTimestamp("resolved_at")?.toInstant(),
            detectedAt = getTimestamp("detected_at").toInstant(),
        )
}
