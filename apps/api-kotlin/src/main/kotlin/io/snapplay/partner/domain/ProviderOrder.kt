package io.snapplay.partner.domain

import java.time.Instant
import java.util.UUID

enum class ProviderOrderStatus {
    PLACED,
    CONFIRMED,
    PREPARING,
    COURIER_ASSIGNED,
    PICKED_UP,
    NEAR_DESTINATION,
    ARRIVED_AT_DESTINATION,
    DELIVERED,
    REJECTED,
    CANCELLED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    FAILED,
    ;

    companion object {
        fun fromEventType(eventType: String): ProviderOrderStatus? =
            when (eventType) {
                "ORDER_PLACED" -> PLACED
                "ORDER_CONFIRMED" -> CONFIRMED
                "ORDER_PREPARING" -> PREPARING
                "ORDER_COURIER_ASSIGNED" -> COURIER_ASSIGNED
                "ORDER_PICKED_UP" -> PICKED_UP
                "ORDER_NEAR_DESTINATION" -> NEAR_DESTINATION
                "ORDER_ARRIVED_AT_DESTINATION" -> ARRIVED_AT_DESTINATION
                "ORDER_DELIVERED" -> DELIVERED
                "ORDER_REJECTED" -> REJECTED
                "ORDER_CANCELLED" -> CANCELLED
                "ORDER_PARTIAL_REFUND" -> PARTIALLY_REFUNDED
                "ORDER_REFUNDED" -> REFUNDED
                "ORDER_FAILED" -> FAILED
                else -> null
            }
    }
}

data class ProviderOrder(
    val id: UUID,
    val connectionId: UUID,
    val handoffId: UUID?,
    val providerOrderRef: String,
    val status: ProviderOrderStatus,
    val currency: String,
    val orderTotalMinor: Long,
    val placedAt: Instant,
    val deliveredAt: Instant?,
)

/** Outcome of a [io.snapplay.partner.application.port.output.ProviderOrderRepository.upsert] call. */
sealed class UpsertOutcome {
    data class Created(val orderId: UUID) : UpsertOutcome()

    data class StatusAdvanced(
        val orderId: UUID,
        val fromStatus: ProviderOrderStatus,
    ) : UpsertOutcome()

    /** New status is not a forward transition from the current state — no change applied. */
    data class OutOfOrder(
        val orderId: UUID,
        val currentStatus: ProviderOrderStatus,
    ) : UpsertOutcome()
}

object OrderStateMachine {
    private val TERMINAL_STATES =
        setOf(
            ProviderOrderStatus.REJECTED,
            ProviderOrderStatus.CANCELLED,
            ProviderOrderStatus.REFUNDED,
            ProviderOrderStatus.FAILED,
        )

    /** Rank within the main delivery flow. Not defined for terminal/refund states. */
    private val MAIN_FLOW_RANK =
        mapOf(
            ProviderOrderStatus.PLACED to 0,
            ProviderOrderStatus.CONFIRMED to 1,
            ProviderOrderStatus.PREPARING to 2,
            ProviderOrderStatus.COURIER_ASSIGNED to 3,
            ProviderOrderStatus.PICKED_UP to 4,
            ProviderOrderStatus.NEAR_DESTINATION to 5,
            ProviderOrderStatus.ARRIVED_AT_DESTINATION to 6,
            ProviderOrderStatus.DELIVERED to 7,
        )

    /**
     * Returns true if transitioning [from] → [to] is a valid forward move.
     *
     * Rules:
     * - Terminal states (REJECTED, CANCELLED, REFUNDED, FAILED) block any further transition.
     * - REJECTED/CANCELLED/FAILED may be applied from any main-flow state (order interruption).
     * - PARTIALLY_REFUNDED and REFUNDED may only follow DELIVERED or PARTIALLY_REFUNDED.
     * - Within the main delivery flow, only strictly forward (higher-rank) moves are allowed.
     * - Out-of-order events (lower or equal rank) are silently ignored (no backward reversal).
     */
    fun isForwardTransition(
        from: ProviderOrderStatus,
        to: ProviderOrderStatus,
    ): Boolean {
        // Already in a terminal state — nothing may follow
        if (from in TERMINAL_STATES) return false

        // Refund progression
        if (from == ProviderOrderStatus.DELIVERED && to in setOf(ProviderOrderStatus.PARTIALLY_REFUNDED, ProviderOrderStatus.REFUNDED)) return true
        if (from == ProviderOrderStatus.PARTIALLY_REFUNDED && to == ProviderOrderStatus.REFUNDED) return true

        // Interruption events allowed from any main-flow state
        if (to in setOf(ProviderOrderStatus.REJECTED, ProviderOrderStatus.CANCELLED, ProviderOrderStatus.FAILED)) {
            return MAIN_FLOW_RANK.containsKey(from)
        }

        // Normal forward progression within the main delivery flow
        val fromRank = MAIN_FLOW_RANK[from] ?: return false
        val toRank = MAIN_FLOW_RANK[to] ?: return false
        return toRank > fromRank
    }
}
