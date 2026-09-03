package io.snapplay.notifications.infrastructure.web

import com.fasterxml.jackson.annotation.JsonInclude
import io.snapplay.notifications.application.port.input.GetSessionMilestoneUseCase
import io.snapplay.notifications.application.port.input.IssuePollingTokenUseCase
import io.snapplay.notifications.domain.OrderMilestone
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/v1/sessions")
class PollingController(
    private val issuePollingToken: IssuePollingTokenUseCase,
    private val getSessionMilestone: GetSessionMilestoneUseCase,
) {
    /** Issues an ephemeral polling token bound exclusively to [sessionId]. Requires normal auth in prod. */
    @PostMapping("/{sessionId}/polling-token")
    fun issueToken(
        @PathVariable sessionId: UUID,
    ): PollingTokenResponse {
        val token = issuePollingToken.issue(sessionId)
        return PollingTokenResponse(token = token.token, expiresAt = token.expiresAt)
    }

    /**
     * Returns the current order milestone for the session bound to [token].
     * Endpoint is open — the token itself is the credential.
     */
    @GetMapping("/milestone")
    fun getMilestone(
        @RequestParam token: String,
    ): SessionMilestoneResponse {
        val result = getSessionMilestone.get(token)
        return SessionMilestoneResponse(
            handoffSessionId = result.handoffSessionId,
            milestone = result.milestone,
            asOf = result.asOf,
        )
    }
}

data class PollingTokenResponse(
    val token: String,
    val expiresAt: Instant,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SessionMilestoneResponse(
    val handoffSessionId: UUID,
    val milestone: OrderMilestone?,
    val asOf: Instant,
)
