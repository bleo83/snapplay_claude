package io.snapplay.links.application.usecase

import io.snapplay.links.application.port.input.ResolveSmartLinkUseCase
import io.snapplay.links.application.port.output.DeepLinkAdapter
import io.snapplay.links.application.port.output.DeepLinkRequest
import io.snapplay.links.application.port.output.ScanEventRecorder
import io.snapplay.links.application.port.output.SmartLinkResolver
import org.springframework.stereotype.Service
import java.net.URI
import java.util.UUID

@Service
class ResolveSmartLinkUseCaseImpl(
    private val smartLinkResolver: SmartLinkResolver,
    private val deepLinkAdapter: DeepLinkAdapter,
    private val scanEventRecorder: ScanEventRecorder,
) : ResolveSmartLinkUseCase {
    override fun resolve(shortCode: String): URI? {
        val resolved = smartLinkResolver.resolveByShortCode(shortCode) ?: return null
        // SNA-23 will persist this token as a handoff_session; for now a per-request UUID is generated
        val trackingToken = UUID.randomUUID().toString()
        val result =
            deepLinkAdapter.build(
                DeepLinkRequest(
                    providerStoreId = resolved.providerStoreId,
                    providerCategoryId = resolved.providerCategoryId,
                    trackingToken = trackingToken,
                    territory = resolved.territory,
                ),
            )
        // Record scan without blocking the redirect — failure here must not fail the redirect
        runCatching { scanEventRecorder.record(resolved) }
        return URI.create(result.webFallbackUrl)
    }
}
