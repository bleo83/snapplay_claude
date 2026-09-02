package io.snapplay.notifications.infrastructure.persistence

import io.snapplay.notifications.application.port.output.SessionMilestonePort
import io.snapplay.notifications.domain.OrderMilestone
import io.snapplay.partner.domain.ProviderOrderStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcSessionMilestonePort(
    private val jdbc: JdbcTemplate,
) : SessionMilestonePort {
    override fun findCurrentMilestone(handoffSessionId: UUID): OrderMilestone? =
        jdbc.query(
            "SELECT status FROM provider_orders WHERE handoff_id = ? ORDER BY placed_at DESC LIMIT 1",
            { rs, _ -> rs.getString("status") },
            handoffSessionId,
        )
            .firstOrNull()
            ?.let { runCatching { ProviderOrderStatus.valueOf(it) }.getOrNull() }
            ?.let { OrderMilestone.fromProviderStatus(it) }
}
