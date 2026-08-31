package io.snapplay.links.application.usecase

import io.snapplay.links.application.port.input.ResolveSmartLinkUseCase
import io.snapplay.links.application.port.output.DeepLinkAdapter
import io.snapplay.links.application.port.output.ScanEventRecorder
import io.snapplay.links.application.port.output.SmartLinkResolver
import org.springframework.stereotype.Service
import java.net.URI

@Service
class ResolveSmartLinkUseCaseImpl(
    private val smartLinkResolver: SmartLinkResolver,
    private val deepLinkAdapter: DeepLinkAdapter,
    private val scanEventRecorder: ScanEventRecorder,
) : ResolveSmartLinkUseCase {
    override fun resolve(shortCode: String): URI? {
        val resolved = smartLinkResolver.resolveByShortCode(shortCode) ?: return null
        val url = deepLinkAdapter.buildUrl(resolved)
        // Record scan without blocking the redirect — failure here must not fail the redirect
        runCatching { scanEventRecorder.record(resolved) }
        return URI.create(url)
    }
}
