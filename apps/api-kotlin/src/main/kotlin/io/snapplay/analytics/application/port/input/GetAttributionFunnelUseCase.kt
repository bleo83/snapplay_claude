package io.snapplay.analytics.application.port.input

import io.snapplay.analytics.domain.AttributionFunnel
import java.time.Instant
import java.util.UUID

interface GetAttributionFunnelUseCase {
    /**
     * Returns the attribution funnel aggregated over [from]..[to].
     *
     * @param connectionId when non-null, restricts the result to a single connection.
     * @param from  start of the period (inclusive).
     * @param to    end of the period (exclusive).
     */
    fun get(
        connectionId: UUID?,
        from: Instant,
        to: Instant,
    ): AttributionFunnel
}
