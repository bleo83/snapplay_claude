package io.snapplay.partner.infrastructure.persistence

import io.snapplay.partner.application.port.output.ProviderOrderRepository
import io.snapplay.partner.application.port.output.ProviderOrderUpsert
import io.snapplay.partner.domain.OrderStateMachine
import io.snapplay.partner.domain.ProviderOrder
import io.snapplay.partner.domain.ProviderOrderStatus
import io.snapplay.partner.domain.UpsertOutcome
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoProviderOrderRepository : ProviderOrderRepository {
    // Keyed by "$connectionId:$providerOrderRef"
    private val store = ConcurrentHashMap<String, ProviderOrder>()

    val orders: Collection<ProviderOrder> get() = store.values

    override fun upsert(
        upsert: ProviderOrderUpsert,
        partnerEventId: UUID?,
    ): UpsertOutcome {
        val key = "${upsert.connectionId}:${upsert.providerOrderRef}"
        val existing = store[key]

        return if (existing == null) {
            val id = UUID.randomUUID()
            store[key] =
                ProviderOrder(
                    id = id,
                    connectionId = upsert.connectionId,
                    handoffId = upsert.handoffId,
                    providerOrderRef = upsert.providerOrderRef,
                    status = upsert.newStatus,
                    currency = upsert.currency,
                    orderTotalMinor = upsert.orderTotalMinor,
                    placedAt = upsert.placedAt,
                    deliveredAt = upsert.deliveredAt,
                )
            UpsertOutcome.Created(id)
        } else if (OrderStateMachine.isForwardTransition(existing.status, upsert.newStatus)) {
            store[key] =
                existing.copy(
                    status = upsert.newStatus,
                    orderTotalMinor = upsert.orderTotalMinor,
                    deliveredAt = upsert.deliveredAt ?: existing.deliveredAt,
                )
            UpsertOutcome.StatusAdvanced(existing.id, existing.status)
        } else {
            UpsertOutcome.OutOfOrder(existing.id, existing.status)
        }
    }

    fun clear() = store.clear()

    fun findByRef(
        connectionId: UUID,
        providerOrderRef: String,
    ): ProviderOrder? = store["$connectionId:$providerOrderRef"]

    fun findByStatus(status: ProviderOrderStatus): List<ProviderOrder> = store.values.filter { it.status == status }
}
