package io.snapplay.notifications.infrastructure.client

import io.snapplay.notifications.application.port.output.DisneyWebhookClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@Component
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoDisneyWebhookClient : DisneyWebhookClient {
    private val log = LoggerFactory.getLogger(DemoDisneyWebhookClient::class.java)

    @Volatile
    var shouldFail: Boolean = false

    private val _deliveries = CopyOnWriteArrayList<Pair<UUID, String>>()
    val deliveries: List<Pair<UUID, String>> get() = _deliveries

    fun reset() {
        shouldFail = false
        _deliveries.clear()
    }

    override fun deliver(
        webhookUrl: String,
        webhookSecret: String,
        notificationId: UUID,
        payload: String,
    ): Boolean {
        return if (shouldFail) {
            log.info("[DEMO] Simulating Disney webhook failure for notification_id={}", notificationId)
            false
        } else {
            log.info("[DEMO] Disney webhook delivered notification_id={}", notificationId)
            _deliveries.add(notificationId to payload)
            true
        }
    }
}
