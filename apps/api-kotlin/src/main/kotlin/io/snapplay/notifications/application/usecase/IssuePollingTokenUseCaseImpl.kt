package io.snapplay.notifications.application.usecase

import io.snapplay.config.SnapPlayProperties
import io.snapplay.notifications.application.port.input.IssuePollingTokenUseCase
import io.snapplay.notifications.application.port.input.PollingToken
import io.snapplay.notifications.application.port.output.PollingTokenRepository
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class IssuePollingTokenUseCaseImpl(
    private val pollingTokenRepo: PollingTokenRepository,
    private val props: SnapPlayProperties,
) : IssuePollingTokenUseCase {
    override fun issue(handoffSessionId: UUID): PollingToken {
        val rawToken = UUID.randomUUID().toString().replace("-", "")
        val tokenHash = sha256Hex(rawToken)
        val expiresAt = Instant.now().plus(props.pollingTokenTtlMinutes, ChronoUnit.MINUTES)
        pollingTokenRepo.save(tokenHash, handoffSessionId, expiresAt)
        return PollingToken(token = rawToken, expiresAt = expiresAt)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
