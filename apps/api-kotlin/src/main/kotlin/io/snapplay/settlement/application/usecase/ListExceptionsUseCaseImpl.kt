package io.snapplay.settlement.application.usecase

import io.snapplay.settlement.application.port.input.ListExceptionsUseCase
import io.snapplay.settlement.application.port.input.ResolveExceptionUseCase
import io.snapplay.settlement.application.port.output.SettlementRepository
import io.snapplay.settlement.domain.ExceptionStatus
import io.snapplay.settlement.domain.SettlementException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class ListExceptionsUseCaseImpl(
    private val repo: SettlementRepository,
) : ListExceptionsUseCase {
    override fun list(
        connectionId: UUID,
        status: ExceptionStatus?,
    ): List<SettlementException> = repo.findExceptionsByConnection(connectionId, status)
}

@Service
class ResolveExceptionUseCaseImpl(
    private val repo: SettlementRepository,
) : ResolveExceptionUseCase {
    override fun resolve(
        exceptionId: UUID,
        status: ExceptionStatus,
        adjustmentId: UUID?,
        note: String,
        resolvedBy: UUID,
    ): SettlementException {
        require(status == ExceptionStatus.RESOLVED || status == ExceptionStatus.WAIVED) {
            "Resolution status must be RESOLVED or WAIVED"
        }
        repo.resolveException(exceptionId, status, adjustmentId, note, resolvedBy, Instant.now())
        return repo.findExceptionsByConnection(UUID.randomUUID(), null)
            .firstOrNull { it.id == exceptionId }
            ?: error("Exception $exceptionId not found after resolution")
    }
}
