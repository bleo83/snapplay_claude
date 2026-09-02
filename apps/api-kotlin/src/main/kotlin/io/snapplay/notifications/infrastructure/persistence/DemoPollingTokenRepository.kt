package io.snapplay.notifications.infrastructure.persistence

import io.snapplay.notifications.application.port.output.PollingTokenRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoPollingTokenRepository : PollingTokenRepository {
    private data class TokenEntry(val handoffSessionId: UUID, val expiresAt: Instant)

    private val store = ConcurrentHashMap<String, TokenEntry>()

    fun clear() = store.clear()

    override fun save(
        tokenHash: String,
        handoffSessionId: UUID,
        expiresAt: Instant,
    ) {
        store[tokenHash] = TokenEntry(handoffSessionId, expiresAt)
    }

    override fun findValidSession(
        tokenHash: String,
        now: Instant,
    ): UUID? {
        val entry = store[tokenHash] ?: return null
        return if (entry.expiresAt.isAfter(now)) entry.handoffSessionId else null
    }
}
