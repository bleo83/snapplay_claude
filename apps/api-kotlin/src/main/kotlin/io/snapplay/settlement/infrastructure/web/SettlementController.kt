package io.snapplay.settlement.infrastructure.web

import io.snapplay.identity.PrincipalResolver
import io.snapplay.settlement.application.port.input.CompareSettlementUseCase
import io.snapplay.settlement.application.port.input.ListExceptionsUseCase
import io.snapplay.settlement.application.port.input.ResolveExceptionUseCase
import io.snapplay.settlement.domain.ComparisonResult
import io.snapplay.settlement.domain.ExceptionStatus
import io.snapplay.settlement.domain.RappiSettlementLine
import io.snapplay.settlement.domain.SettlementException
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
@RequestMapping("/v1/settlement")
class SettlementController(
    private val principalResolver: PrincipalResolver,
    private val compareSettlement: CompareSettlementUseCase,
    private val listExceptions: ListExceptionsUseCase,
    private val resolveException: ResolveExceptionUseCase,
) {
    @PostMapping("/compare")
    @ResponseStatus(HttpStatus.CREATED)
    fun compare(
        @RequestBody request: CompareRequest,
    ): ComparisonResult =
        compareSettlement.compare(
            request.connectionId,
            request.periodFrom,
            request.periodTo,
            request.lines,
        )

    @GetMapping("/exceptions")
    fun exceptions(
        @RequestParam connectionId: UUID,
        @RequestParam(required = false) status: ExceptionStatus?,
    ): List<SettlementException> = listExceptions.list(connectionId, status)

    @PatchMapping("/exceptions/{id}/resolve")
    fun resolve(
        @PathVariable id: UUID,
        @RequestBody request: ResolveRequest,
    ): SettlementException {
        val principal = principalResolver.resolve()
        return resolveException.resolve(id, request.status, request.adjustmentId, request.note, principal.userId)
    }
}

data class CompareRequest(
    val connectionId: UUID,
    val periodFrom: Instant,
    val periodTo: Instant,
    val lines: List<RappiSettlementLine>,
)

data class ResolveRequest(
    val status: ExceptionStatus,
    val adjustmentId: UUID? = null,
    val note: String,
)
