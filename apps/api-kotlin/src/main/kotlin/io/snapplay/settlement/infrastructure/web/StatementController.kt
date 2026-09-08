package io.snapplay.settlement.infrastructure.web

import io.snapplay.identity.PrincipalResolver
import io.snapplay.settlement.application.port.input.ApproveStatementUseCase
import io.snapplay.settlement.application.port.input.GenerateStatementUseCase
import io.snapplay.settlement.application.port.input.GetStatementLinesUseCase
import io.snapplay.settlement.application.port.input.ListStatementsUseCase
import io.snapplay.settlement.domain.Statement
import io.snapplay.settlement.domain.StatementLine
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/v1/statements")
class StatementController(
    private val principalResolver: PrincipalResolver,
    private val generateStatement: GenerateStatementUseCase,
    private val approveStatement: ApproveStatementUseCase,
    private val listStatements: ListStatementsUseCase,
    private val getStatementLines: GetStatementLinesUseCase,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun generate(
        @RequestBody request: GenerateStatementRequest,
    ): Statement {
        val principal = principalResolver.resolve()
        return generateStatement.generate(
            request.partyId,
            request.counterpartyId,
            request.contractId,
            request.periodFrom,
            request.periodTo,
            request.currency,
            principal.userId,
        )
    }

    @GetMapping
    fun list(
        @RequestParam partyId: UUID,
    ): List<Statement> = listStatements.list(partyId)

    @GetMapping("/{id}/lines")
    fun lines(
        @PathVariable id: UUID,
    ): List<StatementLine> = getStatementLines.getLines(id)

    @PatchMapping("/{id}/approve")
    fun approve(
        @PathVariable id: UUID,
    ): Statement {
        val principal = principalResolver.resolve()
        return approveStatement.approve(id, principal.userId)
    }
}

data class GenerateStatementRequest(
    val partyId: UUID,
    val counterpartyId: UUID,
    val contractId: UUID,
    val periodFrom: Instant,
    val periodTo: Instant,
    val currency: String,
)
