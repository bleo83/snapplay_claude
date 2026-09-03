package io.snapplay.notifications.infrastructure.persistence

import io.snapplay.notifications.application.port.output.DisneySubscriptionRepository
import io.snapplay.notifications.domain.DisneySubscription
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcDisneySubscriptionRepository(
    private val jdbc: JdbcTemplate,
) : DisneySubscriptionRepository {
    override fun findByConnectionId(connectionId: UUID): DisneySubscription? =
        jdbc.query(
            "SELECT id, connection_id, webhook_url, webhook_secret FROM disney_subscriptions WHERE connection_id = ?",
            { rs, _ ->
                DisneySubscription(
                    id = UUID.fromString(rs.getString("id")),
                    connectionId = UUID.fromString(rs.getString("connection_id")),
                    webhookUrl = rs.getString("webhook_url"),
                    webhookSecret = rs.getString("webhook_secret"),
                )
            },
            connectionId,
        ).firstOrNull()
}
