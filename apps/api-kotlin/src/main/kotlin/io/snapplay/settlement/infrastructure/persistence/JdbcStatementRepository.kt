package io.snapplay.settlement.infrastructure.persistence

import io.snapplay.settlement.application.port.output.StatementRepository
import io.snapplay.settlement.domain.Statement
import io.snapplay.settlement.domain.StatementLine
import io.snapplay.settlement.domain.StatementStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcStatementRepository(
    private val jdbc: JdbcTemplate,
) : StatementRepository {
    override fun create(statement: Statement): Statement {
        jdbc.update(
            """
            INSERT INTO statements (id, party_id, counterparty_id, contract_id, period_from, period_to,
                currency, total_minor, line_count, status, calculated_by, calculated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            statement.id, statement.partyId, statement.counterpartyId, statement.contractId,
            Timestamp.from(statement.periodFrom), Timestamp.from(statement.periodTo),
            statement.currency, statement.totalMinor, statement.lineCount, statement.status.name,
            statement.calculatedBy, statement.calculatedAt?.let { Timestamp.from(it) },
        )
        return statement
    }

    override fun findById(id: UUID): Statement? = jdbc.query("SELECT * FROM statements WHERE id = ?", { rs, _ -> rs.toStatement() }, id).firstOrNull()

    override fun findByParty(partyId: UUID): List<Statement> =
        jdbc.query("SELECT * FROM statements WHERE party_id = ? ORDER BY created_at DESC", { rs, _ -> rs.toStatement() }, partyId)

    override fun addLine(line: StatementLine) {
        jdbc.update(
            "INSERT INTO statement_lines (id, statement_id, rule_key, description, entry_count, total_minor) VALUES (?, ?, ?, ?, ?, ?)",
            line.id,
            line.statementId,
            line.ruleKey,
            line.description,
            line.entryCount,
            line.totalMinor,
        )
    }

    override fun findLinesByStatement(statementId: UUID): List<StatementLine> =
        jdbc.query(
            "SELECT * FROM statement_lines WHERE statement_id = ?",
            { rs, _ ->
                StatementLine(
                    UUID.fromString(rs.getString("id")),
                    UUID.fromString(rs.getString("statement_id")),
                    rs.getString("rule_key"),
                    rs.getString("description"),
                    rs.getInt("entry_count"),
                    rs.getLong("total_minor"),
                )
            },
            statementId,
        )

    override fun updateStatus(
        id: UUID,
        status: StatementStatus,
        fields: Map<String, Any?>,
    ) {
        val sets = mutableListOf("status = '${status.name}'")
        val params = mutableListOf<Any>()
        fields["checksum"]?.let {
            sets.add("checksum = ?")
            params.add(it)
        }
        fields["line_count"]?.let {
            sets.add("line_count = ?")
            params.add(it)
        }
        fields["approved_by"]?.let {
            sets.add("approved_by = ?")
            params.add(it)
        }
        fields["approved_at"]?.let {
            sets.add("approved_at = ?")
            params.add(Timestamp.from(it as Instant))
        }
        params.add(id)
        jdbc.update("UPDATE statements SET ${sets.joinToString(", ")} WHERE id = ?", *params.toTypedArray())
    }

    private fun ResultSet.toStatement() =
        Statement(
            id = UUID.fromString(getString("id")),
            partyId = UUID.fromString(getString("party_id")),
            counterpartyId = UUID.fromString(getString("counterparty_id")),
            contractId = UUID.fromString(getString("contract_id")),
            periodFrom = getTimestamp("period_from").toInstant(),
            periodTo = getTimestamp("period_to").toInstant(),
            currency = getString("currency"),
            totalMinor = getLong("total_minor"),
            lineCount = getInt("line_count"),
            status = StatementStatus.valueOf(getString("status")),
            calculatedBy = getString("calculated_by")?.let { UUID.fromString(it) },
            approvedBy = getString("approved_by")?.let { UUID.fromString(it) },
            calculatedAt = getTimestamp("calculated_at")?.toInstant(),
            approvedAt = getTimestamp("approved_at")?.toInstant(),
            issuedAt = getTimestamp("issued_at")?.toInstant(),
            paidAt = getTimestamp("paid_at")?.toInstant(),
            paymentRef = getString("payment_ref"),
            checksum = getString("checksum"),
            createdAt = getTimestamp("created_at").toInstant(),
        )
}
