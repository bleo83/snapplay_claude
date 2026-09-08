package io.snapplay.settlement.infrastructure.persistence

import io.snapplay.settlement.application.port.output.StatementRepository
import io.snapplay.settlement.domain.Statement
import io.snapplay.settlement.domain.StatementLine
import io.snapplay.settlement.domain.StatementStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoStatementRepository : StatementRepository {
    private val statements = ConcurrentHashMap<UUID, Statement>()
    private val lines = ConcurrentHashMap<UUID, MutableList<StatementLine>>()

    fun clear() {
        statements.clear()
        lines.clear()
    }

    fun all(): Collection<Statement> = statements.values

    override fun create(statement: Statement): Statement {
        statements[statement.id] = statement
        lines[statement.id] = mutableListOf()
        return statement
    }

    override fun findById(id: UUID): Statement? = statements[id]

    override fun findByParty(partyId: UUID): List<Statement> = statements.values.filter { it.partyId == partyId }.sortedByDescending { it.createdAt }

    override fun addLine(line: StatementLine) {
        lines.getOrPut(line.statementId) { mutableListOf() }.add(line)
    }

    override fun findLinesByStatement(statementId: UUID): List<StatementLine> = lines[statementId] ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    override fun updateStatus(
        id: UUID,
        status: StatementStatus,
        fields: Map<String, Any?>,
    ) {
        statements.computeIfPresent(id) { _, s ->
            var updated = s.copy(status = status)
            fields["checksum"]?.let { updated = updated.copy(checksum = it as String) }
            fields["line_count"]?.let { updated = updated.copy(lineCount = it as Int) }
            fields["approved_by"]?.let { updated = updated.copy(approvedBy = it as UUID) }
            fields["approved_at"]?.let { updated = updated.copy(approvedAt = it as Instant) }
            fields["issued_at"]?.let { updated = updated.copy(issuedAt = it as Instant) }
            updated
        }
    }
}
