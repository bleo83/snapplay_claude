package io.snapplay.settlement.application.port.input

import io.snapplay.settlement.domain.Statement
import io.snapplay.settlement.domain.StatementLine
import java.time.Instant
import java.util.UUID

interface GenerateStatementUseCase {
    fun generate(
        partyId: UUID,
        counterpartyId: UUID,
        contractId: UUID,
        periodFrom: Instant,
        periodTo: Instant,
        currency: String,
        calculatedBy: UUID,
    ): Statement
}

interface ApproveStatementUseCase {
    /** Four-eyes: approver must differ from calculatedBy. */
    fun approve(
        statementId: UUID,
        approvedBy: UUID,
    ): Statement
}

interface ListStatementsUseCase {
    fun list(partyId: UUID): List<Statement>
}

interface GetStatementLinesUseCase {
    fun getLines(statementId: UUID): List<StatementLine>
}
