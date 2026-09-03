package io.snapplay.notifications.application.port.output

import java.time.Instant
import java.util.UUID

interface PollingTokenRepository {
    fun save(
        tokenHash: String,
        handoffSessionId: UUID,
        expiresAt: Instant,
    )

    /** Returns the session ID if the token exists and has not expired; null otherwise. */
    fun findValidSession(
        tokenHash: String,
        now: Instant,
    ): UUID?
}
