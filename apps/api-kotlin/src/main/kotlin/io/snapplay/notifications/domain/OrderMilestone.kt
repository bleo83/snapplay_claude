package io.snapplay.notifications.domain

import io.snapplay.partner.domain.ProviderOrderStatus

/** Order progress events that SnapPlay is permitted to share with Disney. */
enum class OrderMilestone {
    CONFIRMED,
    PICKED_UP,
    NEAR_DESTINATION,
    ARRIVED_AT_DESTINATION,
    DELIVERED,
    ;

    companion object {
        fun fromProviderStatus(status: ProviderOrderStatus): OrderMilestone? =
            when (status) {
                ProviderOrderStatus.CONFIRMED -> CONFIRMED
                ProviderOrderStatus.PICKED_UP -> PICKED_UP
                ProviderOrderStatus.NEAR_DESTINATION -> NEAR_DESTINATION
                ProviderOrderStatus.ARRIVED_AT_DESTINATION -> ARRIVED_AT_DESTINATION
                ProviderOrderStatus.DELIVERED -> DELIVERED
                else -> null
            }
    }
}
