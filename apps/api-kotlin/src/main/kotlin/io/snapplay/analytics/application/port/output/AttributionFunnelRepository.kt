package io.snapplay.analytics.application.port.output

import io.snapplay.analytics.domain.AttributionFunnel
import java.time.Instant
import java.util.UUID

interface AttributionFunnelRepository {
    fun query(
        connectionId: UUID?,
        from: Instant,
        to: Instant,
    ): AttributionFunnel
}
