package io.snapplay.analytics.infrastructure.persistence

import io.snapplay.analytics.application.port.output.AttributionFunnelRepository
import io.snapplay.analytics.domain.AttributionFunnel
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcAttributionFunnelRepository(
    private val jdbc: JdbcTemplate,
) : AttributionFunnelRepository {
    companion object {
        // Base query — only static string literals; all dynamic values flow through ? params
        private val BASE_QUERY =
            """
            SELECT
                COUNT(DISTINCT hs.id)
                    AS scans,
                COUNT(DISTINCT hs.id) FILTER (WHERE hs.status IN ('REDIRECTED', 'CONVERTED'))
                    AS redirects,
                COUNT(DISTINCT hs.id) FILTER (WHERE hs.status = 'CONVERTED')
                    AS converted,
                COUNT(DISTINCT po.id)
                    AS orders_placed,
                COUNT(DISTINCT po.id) FILTER (WHERE po.status = 'DELIVERED')
                    AS orders_delivered,
                SUM(po.order_total_minor) FILTER (WHERE hs.data_sharing_mode != 'AGGREGATED')
                    AS gmv_minor,
                MAX(po.currency)
                    AS currency
            FROM handoff_sessions hs
            LEFT JOIN provider_orders po ON po.handoff_id = hs.id
            WHERE hs.created_at >= ?
              AND hs.created_at < ?
              AND hs.is_bot = false
            """.trimIndent()

        private const val CONNECTION_FILTER = "  AND hs.connection_id = ?"
    }

    @Suppress("SqlSourceToSinkFlow") // sql is built exclusively from static string literals; all dynamic values use ? params — no injection risk
    override fun query(
        connectionId: UUID?,
        from: Instant,
        to: Instant,
    ): AttributionFunnel {
        val sql = if (connectionId != null) "$BASE_QUERY\n$CONNECTION_FILTER" else BASE_QUERY
        val params =
            buildList {
                add(Timestamp.from(from))
                add(Timestamp.from(to))
                if (connectionId != null) add(connectionId)
            }

        return jdbc.queryForObject(sql, { rs, _ ->
            AttributionFunnel(
                connectionId = connectionId,
                from = from,
                to = to,
                scans = rs.getLong("scans"),
                redirects = rs.getLong("redirects"),
                converted = rs.getLong("converted"),
                ordersPlaced = rs.getLong("orders_placed"),
                ordersDelivered = rs.getLong("orders_delivered"),
                gmvMinor = rs.getLong("gmv_minor").takeIf { !rs.wasNull() },
                currency = rs.getString("currency"),
            )
        }, *params.toTypedArray())!!
    }
}
