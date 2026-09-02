package io.snapplay.notifications.infrastructure.scheduler

import io.snapplay.notifications.application.port.output.DisneySubscriptionRepository
import io.snapplay.notifications.application.port.output.DisneyWebhookClient
import io.snapplay.notifications.application.port.output.MilestoneDeliveryRepository
import io.snapplay.notifications.domain.MilestoneDelivery
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom

/**
 * Delivery logic (pick up pending, call client, record result).
 * Kept separate from the `@Scheduled` trigger so tests can call [deliverPending] directly.
 */
@Service
class MilestoneDeliveryService(
    private val deliveryRepo: MilestoneDeliveryRepository,
    private val subscriptionRepo: DisneySubscriptionRepository,
    private val webhookClient: DisneyWebhookClient,
) {
    private val log = LoggerFactory.getLogger(MilestoneDeliveryService::class.java)

    companion object {
        private const val BATCH_SIZE = 20
        private const val MAX_ATTEMPTS = 3

        // Exponential backoff per attempt index (0-based)
        private val BACKOFF =
            listOf(
                Duration.ZERO,
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
            )
        private val JITTER_CEILING_MS = 5_000L
    }

    fun deliverPending() {
        deliveryRepo.findPendingDue(BATCH_SIZE).forEach { delivery -> attemptDelivery(delivery) }
    }

    private fun attemptDelivery(delivery: MilestoneDelivery) {
        val subscription = subscriptionRepo.findByConnectionId(delivery.connectionId)
        if (subscription == null) {
            log.warn("No subscription for connection {} — dead-lettering delivery {}", delivery.connectionId, delivery.id)
            deliveryRepo.markDeadLetter(delivery.id)
            return
        }

        val success =
            runCatching {
                webhookClient.deliver(
                    subscription.webhookUrl,
                    subscription.webhookSecret,
                    delivery.notificationId,
                    delivery.payload,
                )
            }.getOrElse {
                log.warn("Unexpected error delivering milestone notification_id={}", delivery.notificationId, it)
                false
            }

        if (success) {
            deliveryRepo.markDelivered(delivery.id, Instant.now())
            log.info("Delivered milestone notification_id={}", delivery.notificationId)
        } else {
            val nextAttempt = delivery.attemptCount + 1
            if (nextAttempt >= MAX_ATTEMPTS) {
                deliveryRepo.markDeadLetter(delivery.id)
                log.error("Dead-lettered milestone notification_id={} after {} attempts", delivery.notificationId, nextAttempt)
            } else {
                val jitter = Duration.ofMillis(ThreadLocalRandom.current().nextLong(0, JITTER_CEILING_MS))
                val backoff = BACKOFF.getOrElse(nextAttempt) { Duration.ofMinutes(5) }
                deliveryRepo.incrementAttempt(delivery.id, Instant.now().plus(backoff).plus(jitter))
                log.warn("Will retry milestone notification_id={} next attempt={}", delivery.notificationId, nextAttempt)
            }
        }
    }
}
