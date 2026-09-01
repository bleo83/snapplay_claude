package io.snapplay.analytics.application.usecase

import io.snapplay.analytics.application.port.input.GetAttributionFunnelUseCase
import io.snapplay.analytics.application.port.output.AttributionFunnelRepository
import io.snapplay.analytics.domain.AttributionFunnel
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class GetAttributionFunnelUseCaseImpl(
    private val repository: AttributionFunnelRepository,
) : GetAttributionFunnelUseCase {
    override fun get(
        connectionId: UUID?,
        from: Instant,
        to: Instant,
    ): AttributionFunnel = repository.query(connectionId, from, to)
}
