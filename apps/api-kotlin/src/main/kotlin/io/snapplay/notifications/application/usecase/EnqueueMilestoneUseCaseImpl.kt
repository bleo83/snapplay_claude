package io.snapplay.notifications.application.usecase

import com.fasterxml.jackson.databind.ObjectMapper
import io.snapplay.notifications.application.port.input.EnqueueMilestoneUseCase
import io.snapplay.notifications.application.port.output.DisneySubscriptionRepository
import io.snapplay.notifications.application.port.output.MilestoneDeliveryRepository
import io.snapplay.notifications.domain.DeliveryStatus
import io.snapplay.notifications.domain.MilestoneDelivery
import io.snapplay.notifications.domain.OrderMilestone
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class EnqueueMilestoneUseCaseImpl(
    private val deliveryRepo: MilestoneDeliveryRepository,
    private val subscriptionRepo: DisneySubscriptionRepository,
    private val objectMapper: ObjectMapper,
) : EnqueueMilestoneUseCase {
    private val log = LoggerFactory.getLogger(EnqueueMilestoneUseCaseImpl::class.java)

    override fun enqueue(
        connectionId: UUID,
        handoffSessionId: UUID,
        providerOrderId: UUID,
        milestone: OrderMilestone,
        occurredAt: Instant,
    ) {
        subscriptionRepo.findByConnectionId(connectionId) ?: run {
            log.debug("No Disney subscription for connection {} — skipping milestone {}", connectionId, milestone)
            return
        }

        // Deterministic ID: same milestone on the same session always gets the same notification_id
        val notificationId = UUID.nameUUIDFromBytes("$handoffSessionId:${milestone.name}".toByteArray(Charsets.UTF_8))

        if (deliveryRepo.existsByNotificationId(notificationId)) {
            log.debug("Milestone {} already enqueued for session {} — idempotent skip", milestone, handoffSessionId)
            return
        }

        val payload =
            objectMapper.writeValueAsString(
                mapOf(
                    "notification_id" to notificationId,
                    "handoff_session_id" to handoffSessionId,
                    "milestone" to milestone.name,
                    "occurred_at" to occurredAt.toString(),
                ),
            )

        val now = Instant.now()
        deliveryRepo.save(
            MilestoneDelivery(
                id = UUID.randomUUID(),
                notificationId = notificationId,
                connectionId = connectionId,
                handoffSessionId = handoffSessionId,
                providerOrderId = providerOrderId,
                milestone = milestone,
                payload = payload,
                status = DeliveryStatus.PENDING,
                attemptCount = 0,
                nextRetryAt = now,
                deliveredAt = null,
                createdAt = now,
            ),
        )
        log.info(
            "Enqueued milestone delivery notification_id={} milestone={} session={}",
            notificationId,
            milestone,
            handoffSessionId,
        )
    }
}
