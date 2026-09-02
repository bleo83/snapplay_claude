package io.snapplay.notifications.application.port.input

import java.time.Instant
import java.util.UUID

data class PollingToken(val token: String, val expiresAt: Instant)

interface IssuePollingTokenUseCase {
    /** Issues a short-lived opaque token bound exclusively to [handoffSessionId]. */
    fun issue(handoffSessionId: UUID): PollingToken
}
