package io.snapplay.settlement.application.usecase

import io.snapplay.common.NotFoundException
import io.snapplay.common.ValidationException
import io.snapplay.ledger.application.port.output.LedgerRepository
import io.snapplay.settlement.application.port.input.ApproveStatementUseCase
import io.snapplay.settlement.application.port.input.GenerateStatementUseCase
import io.snapplay.settlement.application.port.input.GetStatementLinesUseCase
import io.snapplay.settlement.application.port.input.ListStatementsUseCase
import io.snapplay.settlement.application.port.output.StatementRepository
import io.snapplay.settlement.domain.Statement
import io.snapplay.settlement.domain.StatementLine
import io.snapplay.settlement.domain.StatementStatus
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class GenerateStatementUseCaseImpl(
    private val statementRepo: StatementRepository,
    private val ledgerRepo: LedgerRepository,
) : GenerateStatementUseCase {
    override fun generate(
        partyId: UUID,
        counterpartyId: UUID,
        contractId: UUID,
        periodFrom: Instant,
        periodTo: Instant,
        currency: String,
        calculatedBy: UUID,
    ): Statement {
        val balance = ledgerRepo.getBalance(partyId, currency, periodFrom, periodTo)

        val statement =
            statementRepo.create(
                Statement(
                    id = UUID.randomUUID(),
                    partyId = partyId,
                    counterpartyId = counterpartyId,
                    contractId = contractId,
                    periodFrom = periodFrom,
                    periodTo = periodTo,
                    currency = currency,
                    totalMinor = balance.totalMinor,
                    lineCount = 0,
                    status = StatementStatus.CALCULATED,
                    calculatedBy = calculatedBy,
                    approvedBy = null,
                    calculatedAt = Instant.now(),
                    approvedAt = null,
                    issuedAt = null,
                    paidAt = null,
                    paymentRef = null,
                    checksum = null,
                    createdAt = Instant.now(),
                ),
            )

        // Build lines grouped by rule_key from ledger entries
        val entries = ledgerRepo.getBalance(partyId, currency, periodFrom, periodTo)
        // For the pilot, create a single summary line
        val line =
            StatementLine(
                id = UUID.randomUUID(),
                statementId = statement.id,
                ruleKey = "total",
                description = "Total for period",
                entryCount = 1,
                totalMinor = entries.totalMinor,
            )
        statementRepo.addLine(line)

        // Compute checksum
        val checksum = sha256Hex("${statement.id}:${statement.totalMinor}:${statement.currency}:$periodFrom:$periodTo")
        statementRepo.updateStatus(
            statement.id,
            StatementStatus.REVIEW,
            mapOf("checksum" to checksum, "line_count" to 1),
        )

        return statementRepo.findById(statement.id)!!
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

@Service
class ApproveStatementUseCaseImpl(
    private val repo: StatementRepository,
) : ApproveStatementUseCase {
    override fun approve(
        statementId: UUID,
        approvedBy: UUID,
    ): Statement {
        val statement =
            repo.findById(statementId)
                ?: throw NotFoundException("Statement $statementId not found")

        if (statement.status != StatementStatus.REVIEW) {
            throw ValidationException("Statement is ${statement.status}, expected REVIEW")
        }

        // Four-eyes: approver must differ from calculator
        if (statement.calculatedBy == approvedBy) {
            throw ValidationException("Approver must differ from the person who calculated the statement (four-eyes principle)")
        }

        repo.updateStatus(
            statementId,
            StatementStatus.APPROVED,
            mapOf("approved_by" to approvedBy, "approved_at" to Instant.now()),
        )

        return repo.findById(statementId)!!
    }
}

@Service
class ListStatementsUseCaseImpl(
    private val repo: StatementRepository,
) : ListStatementsUseCase {
    override fun list(partyId: UUID): List<Statement> = repo.findByParty(partyId)
}

@Service
class GetStatementLinesUseCaseImpl(
    private val repo: StatementRepository,
) : GetStatementLinesUseCase {
    override fun getLines(statementId: UUID): List<StatementLine> = repo.findLinesByStatement(statementId)
}
