package io.snapplay.links.infrastructure.persistence

import io.snapplay.links.application.port.output.HandoffSessionRepository
import io.snapplay.links.domain.HandoffSession
import io.snapplay.links.domain.HandoffSessionStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoHandoffSessionRepository : HandoffSessionRepository {
    private val byId = ConcurrentHashMap<UUID, HandoffSession>()

    private val terminalStatuses =
        setOf(
            HandoffSessionStatus.CONVERTED,
            HandoffSessionStatus.EXPIRED,
            HandoffSessionStatus.FAILED,
        )

    override fun create(session: HandoffSession) {
        byId[session.id] = session
    }

    fun findAll(): Collection<HandoffSession> = byId.values

    fun clear() = byId.clear()

    fun findByTokenHash(tokenHash: String): HandoffSession? = byId.values.firstOrNull { it.trackingTokenHash == tokenHash }

    override fun updateStatus(
        id: UUID,
        status: HandoffSessionStatus,
    ) {
        byId.computeIfPresent(id) { _, existing ->
            if (existing.status in terminalStatuses) {
                existing
            } else {
                existing.copy(status = status, updatedAt = Instant.now())
            }
        }
    }
}
