package io.snapplay.notifications.infrastructure.persistence

import io.snapplay.notifications.application.port.output.MilestoneDeliveryRepository
import io.snapplay.notifications.domain.DeliveryStatus
import io.snapplay.notifications.domain.MilestoneDelivery
import io.snapplay.notifications.domain.OrderMilestone
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcMilestoneDeliveryRepository(
    private val jdbc: JdbcTemplate,
) : MilestoneDeliveryRepository {
    override fun save(delivery: MilestoneDelivery) {
        jdbc.update(
            """
            INSERT INTO milestone_deliveries
                (id, notification_id, connection_id, handoff_session_id, provider_order_id,
                 milestone, payload, status, attempt_count, next_retry_at, delivered_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (notification_id) DO NOTHING
            """.trimIndent(),
            delivery.id,
            delivery.notificationId,
            delivery.connectionId,
            delivery.handoffSessionId,
            delivery.providerOrderId,
            delivery.milestone.name,
            delivery.payload,
            delivery.status.name,
            delivery.attemptCount,
            Timestamp.from(delivery.nextRetryAt),
            delivery.deliveredAt?.let { Timestamp.from(it) },
            Timestamp.from(delivery.createdAt),
        )
    }

    override fun existsByNotificationId(notificationId: UUID): Boolean =
        (jdbc.queryForObject("SELECT COUNT(*) FROM milestone_deliveries WHERE notification_id = ?", Int::class.java, notificationId) ?: 0) > 0

    override fun findPendingDue(limit: Int): List<MilestoneDelivery> =
        jdbc.query(
            """
            SELECT * FROM milestone_deliveries
            WHERE status = 'PENDING' AND next_retry_at <= now()
            ORDER BY next_retry_at
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> rs.toDelivery() },
            limit,
        )

    override fun markDelivered(
        id: UUID,
        deliveredAt: Instant,
    ) {
        jdbc.update(
            "UPDATE milestone_deliveries SET status = 'DELIVERED', delivered_at = ?, attempt_count = attempt_count + 1 WHERE id = ?",
            Timestamp.from(deliveredAt),
            id,
        )
    }

    override fun markDeadLetter(id: UUID) {
        jdbc.update(
            "UPDATE milestone_deliveries SET status = 'DEAD_LETTER', attempt_count = attempt_count + 1 WHERE id = ?",
            id,
        )
    }

    override fun incrementAttempt(
        id: UUID,
        nextRetryAt: Instant,
    ) {
        jdbc.update(
            "UPDATE milestone_deliveries SET attempt_count = attempt_count + 1, next_retry_at = ? WHERE id = ?",
            Timestamp.from(nextRetryAt),
            id,
        )
    }

    private fun ResultSet.toDelivery() =
        MilestoneDelivery(
            id = UUID.fromString(getString("id")),
            notificationId = UUID.fromString(getString("notification_id")),
            connectionId = UUID.fromString(getString("connection_id")),
            handoffSessionId = UUID.fromString(getString("handoff_session_id")),
            providerOrderId = UUID.fromString(getString("provider_order_id")),
            milestone = OrderMilestone.valueOf(getString("milestone")),
            payload = getString("payload"),
            status = DeliveryStatus.valueOf(getString("status")),
            attemptCount = getInt("attempt_count"),
            nextRetryAt = getTimestamp("next_retry_at").toInstant(),
            deliveredAt = getTimestamp("delivered_at")?.toInstant(),
            createdAt = getTimestamp("created_at").toInstant(),
        )
}
