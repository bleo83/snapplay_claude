package io.snapplay.settlement.application.usecase

import io.snapplay.settlement.application.port.input.CompareSettlementUseCase
import io.snapplay.settlement.application.port.output.SettlementOrderPort
import io.snapplay.settlement.application.port.output.SettlementRepository
import io.snapplay.settlement.domain.ComparisonResult
import io.snapplay.settlement.domain.ExceptionStatus
import io.snapplay.settlement.domain.RappiSettlementLine
import io.snapplay.settlement.domain.SettlementException
import io.snapplay.settlement.domain.SettlementExceptionType
import io.snapplay.settlement.domain.SettlementReport
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class CompareSettlementUseCaseImpl(
    private val repo: SettlementRepository,
    private val orderPort: SettlementOrderPort,
) : CompareSettlementUseCase {
    private val log = LoggerFactory.getLogger(CompareSettlementUseCaseImpl::class.java)

    companion object {
        private const val AMOUNT_TOLERANCE_MINOR = 1L
    }

    override fun compare(
        connectionId: UUID,
        periodFrom: Instant,
        periodTo: Instant,
        rappiLines: List<RappiSettlementLine>,
    ): ComparisonResult {
        val report =
            repo.createReport(
                SettlementReport(
                    id = UUID.randomUUID(),
                    connectionId = connectionId,
                    periodFrom = periodFrom,
                    periodTo = periodTo,
                    lineCount = rappiLines.size,
                    importedAt = Instant.now(),
                ),
            )

        val ourOrders = orderPort.findOrdersByConnectionAndPeriod(connectionId, periodFrom, periodTo)
        val ourOrdersByRef = ourOrders.associateBy { it.providerOrderRef }
        val rappiByRef = rappiLines.associateBy { it.providerOrderRef }

        var exceptionsFound = 0
        var newExceptions = 0
        val now = Instant.now()

        // Check each Rappi line against our orders
        for (line in rappiLines) {
            val ourOrder = ourOrdersByRef[line.providerOrderRef]
            if (ourOrder == null) {
                if (saveException(
                        report.id,
                        connectionId,
                        line.providerOrderRef,
                        SettlementExceptionType.MISSING_IN_SNAPPLAY,
                        null,
                        line.status,
                        now,
                    )
                ) {
                    newExceptions++
                }
                exceptionsFound++
                continue
            }

            // Status comparison
            if (ourOrder.status.name != line.status) {
                if (saveException(
                        report.id,
                        connectionId,
                        line.providerOrderRef,
                        SettlementExceptionType.STATUS_MISMATCH,
                        ourOrder.status.name,
                        line.status,
                        now,
                    )
                ) {
                    newExceptions++
                }
                exceptionsFound++
            }

            // Amount comparison (with tolerance)
            if (kotlin.math.abs(ourOrder.orderTotalMinor - line.orderTotalMinor) > AMOUNT_TOLERANCE_MINOR) {
                if (saveException(
                        report.id,
                        connectionId,
                        line.providerOrderRef,
                        SettlementExceptionType.AMOUNT_MISMATCH,
                        ourOrder.orderTotalMinor.toString(),
                        line.orderTotalMinor.toString(),
                        now,
                    )
                ) {
                    newExceptions++
                }
                exceptionsFound++
            }

            // Currency comparison
            if (ourOrder.currency != line.currency) {
                if (saveException(
                        report.id,
                        connectionId,
                        line.providerOrderRef,
                        SettlementExceptionType.CURRENCY_MISMATCH,
                        ourOrder.currency,
                        line.currency,
                        now,
                    )
                ) {
                    newExceptions++
                }
                exceptionsFound++
            }
        }

        // Check for orders we have but Rappi doesn't
        for (ourOrder in ourOrders) {
            if (ourOrder.providerOrderRef !in rappiByRef) {
                if (saveException(
                        report.id,
                        connectionId,
                        ourOrder.providerOrderRef,
                        SettlementExceptionType.MISSING_IN_PROVIDER,
                        ourOrder.status.name,
                        null,
                        now,
                    )
                ) {
                    newExceptions++
                }
                exceptionsFound++
            }
        }

        log.info(
            "Settlement comparison complete connection={} period={}/{}: lines={} exceptions={} new={}",
            connectionId,
            periodFrom,
            periodTo,
            rappiLines.size,
            exceptionsFound,
            newExceptions,
        )

        return ComparisonResult(
            reportId = report.id,
            linesCompared = rappiLines.size,
            exceptionsFound = exceptionsFound,
            newExceptions = newExceptions,
        )
    }

    private fun saveException(
        reportId: UUID,
        connectionId: UUID,
        orderRef: String,
        type: SettlementExceptionType,
        expected: String?,
        reported: String?,
        now: Instant,
    ): Boolean =
        repo.saveException(
            SettlementException(
                id = UUID.randomUUID(),
                reportId = reportId,
                connectionId = connectionId,
                providerOrderRef = orderRef,
                exceptionType = type,
                expectedValue = expected,
                reportedValue = reported,
                toleranceAppliedMinor = if (type == SettlementExceptionType.AMOUNT_MISMATCH) AMOUNT_TOLERANCE_MINOR else null,
                status = ExceptionStatus.OPEN,
                resolutionAdjustmentId = null,
                resolutionNote = null,
                resolvedBy = null,
                resolvedAt = null,
                detectedAt = now,
            ),
        )
}
