package io.snapplay.links.infrastructure.deeplink

import io.snapplay.links.application.port.output.DeepLinkAdapter
import io.snapplay.links.application.port.output.DeepLinkRequest
import io.snapplay.links.application.port.output.DeepLinkResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Demo-only adapter. Builds plausible Rappi URLs without real format validation.
 * Replaced by RappiDeepLinkAdapter in non-demo mode.
 */
@Component
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class StubDeepLinkAdapter : DeepLinkAdapter {
    override fun build(request: DeepLinkRequest): DeepLinkResult {
        val store = request.providerStoreId
        val category = request.providerCategoryId
        val token = request.trackingToken
        return DeepLinkResult(
            appDeepLink = "rappi://home/stores/$store?categoriaId=$category&snp_tk=$token",
            webFallbackUrl = "https://www.rappi.com.ar/tiendas/$store?categoriaId=$category&snp_tk=$token",
            adapterVersion = "stub-v1",
        )
    }
}
