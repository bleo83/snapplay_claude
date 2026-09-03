package io.snapplay.partner.infrastructure.persistence

import io.snapplay.partner.application.port.output.ReconciliationCheckpointRepository
import io.snapplay.partner.domain.ReconciliationCheckpoint
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoReconciliationCheckpointRepository : ReconciliationCheckpointRepository {
    private val store = ConcurrentHashMap<UUID, ReconciliationCheckpoint>()

    fun clear() = store.clear()

    fun all(): Collection<ReconciliationCheckpoint> = store.values

    override fun findByConnectionId(connectionId: UUID): ReconciliationCheckpoint? = store[connectionId]

    override fun advance(
        connectionId: UUID,
        reconciledAt: Instant,
    ) {
        store.compute(connectionId) { _, existing ->
            ReconciliationCheckpoint(
                id = existing?.id ?: UUID.randomUUID(),
                connectionId = connectionId,
                lastReconciledAt = reconciledAt,
            )
        }
    }
}
