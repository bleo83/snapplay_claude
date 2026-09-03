package io.snapplay.notifications.infrastructure.persistence

import io.snapplay.notifications.application.port.output.MilestoneDeliveryRepository
import io.snapplay.notifications.domain.DeliveryStatus
import io.snapplay.notifications.domain.MilestoneDelivery
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoMilestoneDeliveryRepository : MilestoneDeliveryRepository {
    private val byId = ConcurrentHashMap<UUID, MilestoneDelivery>()
    private val byNotificationId = ConcurrentHashMap<UUID, UUID>() // notificationId → id

    fun clear() {
        byId.clear()
        byNotificationId.clear()
    }

    fun all(): Collection<MilestoneDelivery> = byId.values

    override fun save(delivery: MilestoneDelivery) {
        // putIfAbsent returns null when the key was absent (inserted); non-null when already present (no-op)
        val inserted = byNotificationId.putIfAbsent(delivery.notificationId, delivery.id) == null
        if (inserted) byId[delivery.id] = delivery
    }

    override fun existsByNotificationId(notificationId: UUID): Boolean = byNotificationId.containsKey(notificationId)

    override fun findPendingDue(limit: Int): List<MilestoneDelivery> =
        byId.values
            .filter { it.status == DeliveryStatus.PENDING && !it.nextRetryAt.isAfter(Instant.now()) }
            .sortedBy { it.nextRetryAt }
            .take(limit)

    override fun markDelivered(
        id: UUID,
        deliveredAt: Instant,
    ) {
        byId.computeIfPresent(id) { _, d ->
            d.copy(status = DeliveryStatus.DELIVERED, deliveredAt = deliveredAt, attemptCount = d.attemptCount + 1)
        }
    }

    override fun markDeadLetter(id: UUID) {
        byId.computeIfPresent(id) { _, d ->
            d.copy(status = DeliveryStatus.DEAD_LETTER, attemptCount = d.attemptCount + 1)
        }
    }

    override fun incrementAttempt(
        id: UUID,
        nextRetryAt: Instant,
    ) {
        byId.computeIfPresent(id) { _, d ->
            d.copy(attemptCount = d.attemptCount + 1, nextRetryAt = nextRetryAt)
        }
    }
}
