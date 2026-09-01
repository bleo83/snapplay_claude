package io.snapplay.partner.infrastructure.persistence

import io.snapplay.links.domain.HandoffSessionStatus
import io.snapplay.links.infrastructure.persistence.DemoHandoffSessionRepository
import io.snapplay.partner.application.port.output.HandoffSessionPort
import io.snapplay.partner.domain.HandoffSessionRef
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoHandoffSessionPort(
    private val demoRepo: DemoHandoffSessionRepository,
) : HandoffSessionPort {
    override fun findByTokenHash(tokenHash: String): HandoffSessionRef? {
        val session = demoRepo.findByTokenHash(tokenHash) ?: return null
        val isTerminal =
            session.status in
                setOf(
                    HandoffSessionStatus.CONVERTED,
                    HandoffSessionStatus.EXPIRED,
                    HandoffSessionStatus.FAILED,
                )
        if (isTerminal || session.expiresAt.isBefore(Instant.now())) return null
        return HandoffSessionRef(id = session.id, connectionId = session.connectionId)
    }

    override fun markConverted(id: UUID) {
        demoRepo.updateStatus(id, HandoffSessionStatus.CONVERTED)
    }
}
