package io.snapplay.settlement.domain

import java.time.Instant
import java.util.UUID

enum class StatementStatus {
    OPEN,
    CALCULATED,
    REVIEW,
    APPROVED,
    ISSUED,
    PAID,
    CLOSED,
}

data class Statement(
    val id: UUID,
    val partyId: UUID,
    val counterpartyId: UUID,
    val contractId: UUID,
    val periodFrom: Instant,
    val periodTo: Instant,
    val currency: String,
    val totalMinor: Long,
    val lineCount: Int,
    val status: StatementStatus,
    val calculatedBy: UUID?,
    val approvedBy: UUID?,
    val calculatedAt: Instant?,
    val approvedAt: Instant?,
    val issuedAt: Instant?,
    val paidAt: Instant?,
    val paymentRef: String?,
    val checksum: String?,
    val createdAt: Instant,
)

data class StatementLine(
    val id: UUID,
    val statementId: UUID,
    val ruleKey: String,
    val description: String,
    val entryCount: Int,
    val totalMinor: Long,
)
