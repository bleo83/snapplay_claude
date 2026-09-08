package io.snapplay.settlement.application.port.output

import io.snapplay.settlement.domain.ExceptionStatus
import io.snapplay.settlement.domain.SettlementException
import io.snapplay.settlement.domain.SettlementReport
import java.time.Instant
import java.util.UUID

interface SettlementRepository {
    fun createReport(report: SettlementReport): SettlementReport

    /** Append-only insert. Returns false on duplicate (report_id + order_ref + type). */
    fun saveException(exception: SettlementException): Boolean

    fun findExceptionsByReport(reportId: UUID): List<SettlementException>

    fun findExceptionsByConnection(
        connectionId: UUID,
        status: ExceptionStatus?,
    ): List<SettlementException>

    fun resolveException(
        id: UUID,
        status: ExceptionStatus,
        adjustmentId: UUID?,
        note: String,
        resolvedBy: UUID,
        resolvedAt: Instant,
    )
}
