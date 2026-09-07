package io.snapplay.links.application.usecase

import io.snapplay.config.SnapPlayProperties
import io.snapplay.links.application.port.input.ResolveSmartLinkUseCase
import io.snapplay.links.application.port.output.DeepLinkAdapter
import io.snapplay.links.application.port.output.DeepLinkRequest
import io.snapplay.links.application.port.output.HandoffSessionRepository
import io.snapplay.links.application.port.output.ScanEventRecorder
import io.snapplay.links.application.port.output.SmartLinkResolver
import io.snapplay.links.domain.HandoffSession
import io.snapplay.links.domain.HandoffSessionStatus
import org.springframework.stereotype.Service
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class ResolveSmartLinkUseCaseImpl(
    private val smartLinkResolver: SmartLinkResolver,
    private val deepLinkAdapter: DeepLinkAdapter,
    private val scanEventRecorder: ScanEventRecorder,
    private val handoffSessionRepository: HandoffSessionRepository,
    private val props: SnapPlayProperties,
) : ResolveSmartLinkUseCase {
    override fun resolve(
        shortCode: String,
        isBot: Boolean,
    ): URI? {
        val resolved = smartLinkResolver.resolveByShortCode(shortCode) ?: return null

        // Raw token: 32 hex chars (UUID without dashes) — satisfies SAFE_VALUE regex in RappiDeepLinkAdapter.
        // Only the SHA-256 hash is persisted; the raw token is never stored.
        val rawToken = UUID.randomUUID().toString().replace("-", "")
        val tokenHash = sha256Hex(rawToken)

        val now = Instant.now()
        val session =
            HandoffSession(
                id = UUID.randomUUID(),
                smartLinkId = resolved.smartLinkId,
                experienceVersionId = resolved.experienceVersionId,
                connectionId = resolved.connectionId,
                trackingTokenHash = tokenHash,
                dataSharingMode = resolved.dataSharingMode,
                status = HandoffSessionStatus.CREATED,
                isBot = isBot,
                expiresAt = now.plus(props.handoffSessionTtlMinutes, ChronoUnit.MINUTES),
                createdAt = now,
                updatedAt = now,
            )

        // Attribution confirmed in store before the redirect is issued — never redirect without it
        handoffSessionRepository.create(session)

        val result =
            runCatching {
                deepLinkAdapter.build(
                    DeepLinkRequest(
                        providerStoreId = resolved.providerStoreId,
                        providerCategoryId = resolved.providerCategoryId,
                        trackingToken = rawToken,
                        territory = resolved.territory,
                    ),
                )
            }.getOrElse { ex ->
                // Adapter failure is auditable — session stays in DB with FAILED status
                runCatching { handoffSessionRepository.updateStatus(session.id, HandoffSessionStatus.FAILED) }
                throw ex
            }

        // Best-effort post-redirect state updates — failures must not block the response
        runCatching { handoffSessionRepository.updateStatus(session.id, HandoffSessionStatus.REDIRECTED) }
        runCatching { scanEventRecorder.record(resolved) }

        return URI.create(result.webFallbackUrl)
    }

    /** SHA-256 hex of [input]. Raw tracking tokens are never persisted — only their hash is stored. */
    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
