package io.snapplay.partner.infrastructure.client

import io.snapplay.partner.application.port.output.RappiOrdersClient
import io.snapplay.partner.domain.RappiOrderPage
import io.snapplay.partner.domain.RappiOrderSnapshot
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@Component
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoRappiOrdersClient : RappiOrdersClient {
    private val pages = CopyOnWriteArrayList<RappiOrderPage>()
    private var callCount = 0

    fun reset() {
        pages.clear()
        callCount = 0
    }

    /** Enqueues pages to be returned sequentially on each call to [fetchOrders]. */
    fun enqueuePages(vararg newPages: RappiOrderPage) {
        pages.addAll(newPages)
    }

    /** Convenience: enqueue a single page with the given orders and no next cursor. */
    fun enqueueOrders(vararg orders: RappiOrderSnapshot) {
        pages.add(RappiOrderPage(orders = orders.toList(), nextCursor = null))
    }

    override fun fetchOrders(
        connectionId: UUID,
        from: Instant,
        to: Instant,
        cursor: String?,
    ): RappiOrderPage {
        val index = callCount++
        return pages.getOrElse(index) { RappiOrderPage(emptyList(), null) }
    }
}
