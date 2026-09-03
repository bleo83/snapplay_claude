package io.snapplay.notifications.domain

import java.time.Instant
import java.util.UUID

enum class DeliveryStatus { PENDING, DELIVERED, DEAD_LETTER }

data class MilestoneDelivery(
    val id: UUID,
    /** Deterministic key reused across retries: UUIDv3 of "$handoffSessionId:$milestone". */
    val notificationId: UUID,
    val connectionId: UUID,
    val handoffSessionId: UUID,
    val providerOrderId: UUID,
    val milestone: OrderMilestone,
    /** Pre-serialised JSON body sent verbatim to Disney on every attempt. */
    val payload: String,
    val status: DeliveryStatus,
    val attemptCount: Int,
    val nextRetryAt: Instant,
    val deliveredAt: Instant?,
    val createdAt: Instant,
)
