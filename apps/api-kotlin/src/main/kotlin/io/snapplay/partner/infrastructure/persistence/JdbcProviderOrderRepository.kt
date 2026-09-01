package io.snapplay.partner.infrastructure.persistence

import io.snapplay.partner.application.port.output.ProviderOrderRepository
import io.snapplay.partner.application.port.output.ProviderOrderUpsert
import io.snapplay.partner.domain.OrderStateMachine
import io.snapplay.partner.domain.ProviderOrder
import io.snapplay.partner.domain.ProviderOrderStatus
import io.snapplay.partner.domain.UpsertOutcome
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcProviderOrderRepository(
    private val jdbc: JdbcTemplate,
) : ProviderOrderRepository {
    override fun upsert(
        upsert: ProviderOrderUpsert,
        partnerEventId: UUID,
    ): UpsertOutcome {
        val existing = findByRef(upsert.connectionId, upsert.providerOrderRef)

        return if (existing == null) {
            val newId = insert(upsert)
            insertHistory(newId, fromStatus = null, toStatus = upsert.newStatus, partnerEventId, upsert.placedAt)
            UpsertOutcome.Created(newId)
        } else if (OrderStateMachine.isForwardTransition(existing.status, upsert.newStatus)) {
            advance(existing.id, upsert)
            insertHistory(existing.id, fromStatus = existing.status, toStatus = upsert.newStatus, partnerEventId, upsert.placedAt)
            UpsertOutcome.StatusAdvanced(existing.id, existing.status)
        } else {
            UpsertOutcome.OutOfOrder(existing.id, existing.status)
        }
    }

    private fun findByRef(
        connectionId: UUID,
        providerOrderRef: String,
    ): ProviderOrder? =
        jdbc.query(
            """
            SELECT id, connection_id, handoff_id, provider_order_ref, status,
                   currency, order_total_minor, placed_at, delivered_at
              FROM provider_orders
             WHERE connection_id = ? AND provider_order_ref = ?
            LIMIT 1
            """.trimIndent(),
            { rs, _ ->
                ProviderOrder(
                    id = UUID.fromString(rs.getString("id")),
                    connectionId = UUID.fromString(rs.getString("connection_id")),
                    handoffId = rs.getString("handoff_id")?.let { UUID.fromString(it) },
                    providerOrderRef = rs.getString("provider_order_ref"),
                    status = ProviderOrderStatus.valueOf(rs.getString("status")),
                    currency = rs.getString("currency"),
                    orderTotalMinor = rs.getLong("order_total_minor"),
                    placedAt = rs.getTimestamp("placed_at").toInstant(),
                    deliveredAt = rs.getTimestamp("delivered_at")?.toInstant(),
                )
            },
            connectionId,
            providerOrderRef,
        ).firstOrNull()

    private fun insert(upsert: ProviderOrderUpsert): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO provider_orders
                (id, connection_id, handoff_id, provider_order_ref, status,
                 currency, order_total_minor, placed_at, delivered_at)
            VALUES (?, ?, ?, ?, ?::order_status, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            upsert.connectionId,
            upsert.handoffId,
            upsert.providerOrderRef,
            upsert.newStatus.name,
            upsert.currency,
            upsert.orderTotalMinor,
            Timestamp.from(upsert.placedAt),
            upsert.deliveredAt?.let { Timestamp.from(it) },
        )
        return id
    }

    private fun advance(
        orderId: UUID,
        upsert: ProviderOrderUpsert,
    ) {
        jdbc.update(
            """
            UPDATE provider_orders
               SET status = ?::order_status,
                   order_total_minor = ?,
                   delivered_at = COALESCE(?, delivered_at),
                   updated_at = now()
             WHERE id = ?
            """.trimIndent(),
            upsert.newStatus.name,
            upsert.orderTotalMinor,
            upsert.deliveredAt?.let { Timestamp.from(it) },
            orderId,
        )
    }

    private fun insertHistory(
        orderId: UUID,
        fromStatus: ProviderOrderStatus?,
        toStatus: ProviderOrderStatus,
        partnerEventId: UUID,
        occurredAt: Instant,
    ) {
        jdbc.update(
            """
            INSERT INTO order_status_history
                (provider_order_id, partner_event_id, from_status, to_status, occurred_at)
            VALUES (?, ?, ?::order_status, ?::order_status, ?)
            ON CONFLICT (provider_order_id, to_status, occurred_at) DO NOTHING
            """.trimIndent(),
            orderId,
            partnerEventId,
            fromStatus?.name,
            toStatus.name,
            Timestamp.from(occurredAt),
        )
    }
}
