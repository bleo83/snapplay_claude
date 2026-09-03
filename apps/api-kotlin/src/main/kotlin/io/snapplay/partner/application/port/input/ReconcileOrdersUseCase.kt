package io.snapplay.partner.application.port.input

import io.snapplay.partner.domain.ReconciliationResult
import java.time.Instant
import java.util.UUID

interface ReconcileOrdersUseCase {
    /**
     * Pulls orders from Rappi for [connectionId] and reconciles against local state.
     *
     * If [from]/[to] are null, the window is derived from the stored checkpoint
     * (or a default lookback period) through now.
     */
    fun reconcile(
        connectionId: UUID,
        from: Instant? = null,
        to: Instant? = null,
    ): ReconciliationResult
}
