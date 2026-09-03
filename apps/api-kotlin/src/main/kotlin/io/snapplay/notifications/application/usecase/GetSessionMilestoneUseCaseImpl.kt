package io.snapplay.notifications.application.usecase

import io.snapplay.common.UnauthorizedException
import io.snapplay.notifications.application.port.input.GetSessionMilestoneUseCase
import io.snapplay.notifications.application.port.input.SessionMilestone
import io.snapplay.notifications.application.port.output.PollingTokenRepository
import io.snapplay.notifications.application.port.output.SessionMilestonePort
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant

@Service
class GetSessionMilestoneUseCaseImpl(
    private val pollingTokenRepo: PollingTokenRepository,
    private val sessionMilestonePort: SessionMilestonePort,
) : GetSessionMilestoneUseCase {
    override fun get(token: String): SessionMilestone {
        val tokenHash = sha256Hex(token)
        val now = Instant.now()
        val sessionId =
            pollingTokenRepo.findValidSession(tokenHash, now)
                ?: throw UnauthorizedException("Invalid or expired polling token")
        val milestone = sessionMilestonePort.findCurrentMilestone(sessionId)
        return SessionMilestone(handoffSessionId = sessionId, milestone = milestone, asOf = now)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
