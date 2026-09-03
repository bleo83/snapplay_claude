package io.snapplay.partner.application.port.output

import io.snapplay.partner.domain.RappiOrderPage
import java.time.Instant
import java.util.UUID

interface RappiOrdersClient {
    /**
     * Fetches a page of orders from Rappi for the given connection and date range.
     * Returns an empty page when no more results are available.
     */
    fun fetchOrders(
        connectionId: UUID,
        from: Instant,
        to: Instant,
        cursor: String? = null,
    ): RappiOrderPage
}
