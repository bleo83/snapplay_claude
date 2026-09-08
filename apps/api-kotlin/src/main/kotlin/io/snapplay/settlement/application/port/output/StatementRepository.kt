package io.snapplay.settlement.application.port.output

import io.snapplay.settlement.domain.Statement
import io.snapplay.settlement.domain.StatementLine
import io.snapplay.settlement.domain.StatementStatus
import java.util.UUID

interface StatementRepository {
    fun create(statement: Statement): Statement

    fun findById(id: UUID): Statement?

    fun findByParty(partyId: UUID): List<Statement>

    fun addLine(line: StatementLine)

    fun findLinesByStatement(statementId: UUID): List<StatementLine>

    fun updateStatus(
        id: UUID,
        status: StatementStatus,
        fields: Map<String, Any?>,
    )
}
