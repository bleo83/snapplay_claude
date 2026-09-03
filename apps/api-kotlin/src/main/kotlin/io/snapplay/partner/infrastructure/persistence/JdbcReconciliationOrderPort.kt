package io.snapplay.partner.infrastructure.persistence

import io.snapplay.partner.application.port.output.ReconciliationOrderPort
import io.snapplay.partner.domain.ProviderOrder
import io.snapplay.partner.domain.ProviderOrderStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcReconciliationOrderPort(
    private val jdbc: JdbcTemplate,
) : ReconciliationOrderPort {
    override fun findByRef(
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
}
