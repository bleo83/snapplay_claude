package io.snapplay.settlement.infrastructure.persistence

import io.snapplay.settlement.application.port.output.SettlementRepository
import io.snapplay.settlement.domain.ExceptionStatus
import io.snapplay.settlement.domain.SettlementException
import io.snapplay.settlement.domain.SettlementReport
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoSettlementRepository : SettlementRepository {
    private val reports = ConcurrentHashMap<UUID, SettlementReport>()
    private val exceptions = ConcurrentHashMap<UUID, SettlementException>()

    fun clear() {
        reports.clear()
        exceptions.clear()
    }

    fun allExceptions(): Collection<SettlementException> = exceptions.values

    override fun createReport(report: SettlementReport): SettlementReport {
        reports[report.id] = report
        return report
    }

    override fun saveException(exception: SettlementException): Boolean {
        // Idempotent: skip if an OPEN exception already exists for this (connection, order, type)
        val duplicate =
            exceptions.values.any {
                it.connectionId == exception.connectionId &&
                    it.providerOrderRef == exception.providerOrderRef &&
                    it.exceptionType == exception.exceptionType &&
                    it.status == ExceptionStatus.OPEN
            }
        if (duplicate) return false
        exceptions[exception.id] = exception
        return true
    }

    override fun findExceptionsByReport(reportId: UUID): List<SettlementException> =
        exceptions.values.filter { it.reportId == reportId }.sortedBy { it.detectedAt }

    override fun findExceptionsByConnection(
        connectionId: UUID,
        status: ExceptionStatus?,
    ): List<SettlementException> =
        exceptions.values
            .filter { it.connectionId == connectionId }
            .filter { status == null || it.status == status }
            .sortedByDescending { it.detectedAt }

    override fun resolveException(
        id: UUID,
        status: ExceptionStatus,
        adjustmentId: UUID?,
        note: String,
        resolvedBy: UUID,
        resolvedAt: Instant,
    ) {
        exceptions.computeIfPresent(id) { _, e ->
            e.copy(status = status, resolutionAdjustmentId = adjustmentId, resolutionNote = note, resolvedBy = resolvedBy, resolvedAt = resolvedAt)
        }
    }
}
