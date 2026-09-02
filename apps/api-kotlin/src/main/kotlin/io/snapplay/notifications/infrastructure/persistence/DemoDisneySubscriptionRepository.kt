package io.snapplay.notifications.infrastructure.persistence

import io.snapplay.notifications.application.port.output.DisneySubscriptionRepository
import io.snapplay.notifications.domain.DisneySubscription
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoDisneySubscriptionRepository : DisneySubscriptionRepository {
    companion object {
        val DEMO_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000099")
        const val DEMO_WEBHOOK_URL = "https://disney.example.com/webhook"
        const val DEMO_WEBHOOK_SECRET = "demo-disney-secret"
    }

    override fun findByConnectionId(connectionId: UUID): DisneySubscription =
        DisneySubscription(
            id = DEMO_ID,
            connectionId = connectionId,
            webhookUrl = DEMO_WEBHOOK_URL,
            webhookSecret = DEMO_WEBHOOK_SECRET,
        )
}
