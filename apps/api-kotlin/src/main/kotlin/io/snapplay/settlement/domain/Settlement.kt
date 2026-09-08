package io.snapplay.settlement.domain

import java.time.Instant
import java.util.UUID

enum class SettlementExceptionType {
    MISSING_IN_SNAPPLAY,
    MISSING_IN_PROVIDER,
    STATUS_MISMATCH,
    AMOUNT_MISMATCH,
    CURRENCY_MISMATCH,
    FEE_MISMATCH,
    LATE_REFUND,
    DUPLICATE_ATTRIBUTION,
}

enum class ExceptionStatus { OPEN, UNDER_REVIEW, RESOLVED, WAIVED }

data class SettlementReport(
    val id: UUID,
    val connectionId: UUID,
    val periodFrom: Instant,
    val periodTo: Instant,
    val lineCount: Int,
    val importedAt: Instant,
)

data class SettlementException(
    val id: UUID,
    val reportId: UUID,
    val connectionId: UUID,
    val providerOrderRef: String,
    val exceptionType: SettlementExceptionType,
    val expectedValue: String?,
    val reportedValue: String?,
    val toleranceAppliedMinor: Long?,
    val status: ExceptionStatus,
    val resolutionAdjustmentId: UUID?,
    val resolutionNote: String?,
    val resolvedBy: UUID?,
    val resolvedAt: Instant?,
    val detectedAt: Instant,
)

/** A single line from Rappi's settlement report. */
data class RappiSettlementLine(
    val providerOrderRef: String,
    val status: String,
    val orderTotalMinor: Long,
    val feeMinor: Long,
    val currency: String,
    val placedAt: Instant,
    val deliveredAt: Instant?,
)

data class ComparisonResult(
    val reportId: UUID,
    val linesCompared: Int,
    val exceptionsFound: Int,
    val newExceptions: Int,
)
