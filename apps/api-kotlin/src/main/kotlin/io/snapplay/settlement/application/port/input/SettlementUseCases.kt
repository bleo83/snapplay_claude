package io.snapplay.settlement.application.port.input

import io.snapplay.settlement.domain.ComparisonResult
import io.snapplay.settlement.domain.ExceptionStatus
import io.snapplay.settlement.domain.RappiSettlementLine
import io.snapplay.settlement.domain.SettlementException
import java.time.Instant
import java.util.UUID

interface CompareSettlementUseCase {
    fun compare(
        connectionId: UUID,
        periodFrom: Instant,
        periodTo: Instant,
        rappiLines: List<RappiSettlementLine>,
    ): ComparisonResult
}

interface ListExceptionsUseCase {
    fun list(
        connectionId: UUID,
        status: ExceptionStatus? = null,
    ): List<SettlementException>
}

interface ResolveExceptionUseCase {
    fun resolve(
        exceptionId: UUID,
        status: ExceptionStatus,
        adjustmentId: UUID? = null,
        note: String,
        resolvedBy: UUID,
    ): SettlementException
}
