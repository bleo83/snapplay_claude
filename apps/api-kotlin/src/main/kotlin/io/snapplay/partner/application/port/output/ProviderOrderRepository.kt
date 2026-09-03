package io.snapplay.partner.application.port.output

import io.snapplay.partner.domain.ProviderOrderStatus
import io.snapplay.partner.domain.UpsertOutcome
import java.time.Instant
import java.util.UUID

data class ProviderOrderUpsert(
    val connectionId: UUID,
    val handoffId: UUID?,
    val providerOrderRef: String,
    val newStatus: ProviderOrderStatus,
    val currency: String,
    val orderTotalMinor: Long,
    val placedAt: Instant,
    val deliveredAt: Instant?,
)

interface ProviderOrderRepository {
    /**
     * Creates or advances the state of a provider order.
     *
     * If no order exists for (connectionId, providerOrderRef), inserts a new one.
     * If one exists, advances its status only if [ProviderOrderUpsert.newStatus] is a
     * forward transition per [io.snapplay.partner.domain.OrderStateMachine.isForwardTransition].
     * Out-of-order events are silently accepted (no DB change) and return [UpsertOutcome.OutOfOrder].
     *
     * @param partnerEventId linked for the status-history audit trail
     */
    fun upsert(
        upsert: ProviderOrderUpsert,
        partnerEventId: UUID?,
    ): UpsertOutcome
}
