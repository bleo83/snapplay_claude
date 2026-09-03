package io.snapplay.notifications.application.port.input

import io.snapplay.notifications.domain.OrderMilestone
import java.time.Instant
import java.util.UUID

interface EnqueueMilestoneUseCase {
    /**
     * Enqueues an outbound milestone notification to Disney for the given session.
     * No-ops if the connection has no Disney subscription, or if the milestone was already enqueued.
     */
    fun enqueue(
        connectionId: UUID,
        handoffSessionId: UUID,
        providerOrderId: UUID,
        milestone: OrderMilestone,
        occurredAt: Instant,
    )
}
