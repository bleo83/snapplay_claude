package io.snapplay.links.infrastructure.persistence

import io.snapplay.links.application.port.output.SmartLinkResolver
import io.snapplay.links.domain.ResolvedSmartLink
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// Demo connection and version IDs are fixed for the in-memory dataset
private val DEMO_CONNECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001")
private val DEMO_VERSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002")

/**
 * In-memory resolver for demo mode. Seeded with links backed by PUBLISHED demo experiences.
 * [registerResolved] is called by [DemoSmartLinkRepository] when a new link is created so
 * that it becomes immediately resolvable — safe because creation validates PUBLISHED status.
 */
@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoSmartLinkResolver : SmartLinkResolver {
    private val resolvedByCode: ConcurrentHashMap<String, ResolvedSmartLink> =
        ConcurrentHashMap(
            mapOf(
                // Toy Story Movie Night — PUBLISHED experience
                "7E1vM2kP9xQ4" to
                    ResolvedSmartLink(
                        smartLinkId = UUID.fromString("9ab6732c-093d-4fee-8efa-b5d2b6ec4d1f"),
                        shortCode = "7E1vM2kP9xQ4",
                        experienceVersionId = DEMO_VERSION_ID,
                        connectionId = DEMO_CONNECTION_ID,
                        providerStoreId = "900000",
                        providerCategoryId = "2000",
                        handoffMode = "STORE_DEEPLINK",
                        territory = "AR",
                    ),
                // Moana Family Night is intentionally excluded — its experience is DRAFT
            ),
        )

    override fun resolveByShortCode(shortCode: String): ResolvedSmartLink? = resolvedByCode[shortCode]

    /** Called by DemoSmartLinkRepository after a new link is created against a PUBLISHED experience. */
    fun registerResolved(resolved: ResolvedSmartLink) {
        resolvedByCode[resolved.shortCode] = resolved
    }
}
