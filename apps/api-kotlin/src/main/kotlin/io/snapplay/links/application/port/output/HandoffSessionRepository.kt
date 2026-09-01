package io.snapplay.links.application.port.output

import io.snapplay.links.domain.HandoffSession
import io.snapplay.links.domain.HandoffSessionStatus
import java.util.UUID

interface HandoffSessionRepository {
    /**
     * Persists a new session. Throws on duplicate [HandoffSession.trackingTokenHash]
     * (UNIQUE violation) — UUID collision probability is negligible.
     */
    fun create(session: HandoffSession)

    /**
     * Transitions status. No-op if the session is already in a terminal state
     * (CONVERTED, EXPIRED, FAILED) — prevents backward transitions without locking.
     */
    fun updateStatus(
        id: UUID,
        status: HandoffSessionStatus,
    )
}
