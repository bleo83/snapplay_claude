package io.snapplay.notifications.application.port.input

import io.snapplay.notifications.domain.OrderMilestone
import java.time.Instant
import java.util.UUID

data class SessionMilestone(
    val handoffSessionId: UUID,
    /** Null when no milestone has been reached yet for this session. */
    val milestone: OrderMilestone?,
    val asOf: Instant,
)

interface GetSessionMilestoneUseCase {
    /**
     * Validates the ephemeral [token] and returns the current order milestone for the bound session.
     * Throws [io.snapplay.common.UnauthorizedException] if the token is missing, expired, or unknown.
     */
    fun get(token: String): SessionMilestone
}
