package io.snapplay.analytics.domain

import java.time.Instant
import java.util.UUID

/**
 * Aggregated attribution funnel for the pilot.
 *
 * Denominators:
 * - [scans]           = handoff sessions created in the period (one per QR scan).
 * - [redirects]       = sessions that reached REDIRECTED or CONVERTED status.
 * - [converted]       = sessions with a linked provider_order (status = CONVERTED).
 * - [ordersPlaced]    = distinct provider_orders created in the period.
 * - [ordersDelivered] = provider_orders with status DELIVERED.
 *
 * [gmvMinor] and [currency] are null when the connection's data_sharing_mode is AGGREGATED.
 */
data class AttributionFunnel(
    val connectionId: UUID?,
    val from: Instant,
    val to: Instant,
    val scans: Long,
    val redirects: Long,
    val converted: Long,
    val ordersPlaced: Long,
    val ordersDelivered: Long,
    /** Sum of order_total_minor for the period. Null for AGGREGATED connections. */
    val gmvMinor: Long?,
    /** Currency of the GMV figure (null when gmvMinor is null or no orders exist). */
    val currency: String?,
)
