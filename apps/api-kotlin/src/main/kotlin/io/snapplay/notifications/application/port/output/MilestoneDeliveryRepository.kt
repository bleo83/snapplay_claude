package io.snapplay.notifications.application.port.output

import io.snapplay.notifications.domain.MilestoneDelivery
import java.time.Instant
import java.util.UUID

interface MilestoneDeliveryRepository {
    fun save(delivery: MilestoneDelivery)

    fun existsByNotificationId(notificationId: UUID): Boolean

    /** Returns up to [limit] PENDING deliveries whose next_retry_at ≤ now. */
    fun findPendingDue(limit: Int): List<MilestoneDelivery>

    fun markDelivered(
        id: UUID,
        deliveredAt: Instant,
    )

    fun markDeadLetter(id: UUID)

    fun incrementAttempt(
        id: UUID,
        nextRetryAt: Instant,
    )
}
